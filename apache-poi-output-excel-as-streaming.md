# Apache POI 以流（Stream）的方式输出Excel

源于一个很BT的需求：从数据库查出大量数据（百万级）导出成Excel（不讨论业务的正确性）。我们时想一边查数据一边以流（Stream）的方式输出到客户端。

## 30秒结论
官方不提供以流的方式输出，但我们可以通过修改源码来做到，不过是有限制条件。不想看的可以直接参考[Link](https://github.com/chaodongyue/blog/blob/master/java/excel/StreamSXSSFWorkbook.java)

## 前言

xlsx格式是 office 2007 开始使用的 Office Open XML 标准([WIKI](https://zh.wikipedia.org/wiki/Office_Open_XML))，xlsx 其实是一个压缩包，大家可以解压出来看到里面的内容。

Java开源导出Excel只有Apache POI这个选择。众所周知POI导出大量的数据会导致OOM。
究其原因是从创建 Workbook（org.apache.poi.xssf.usermodel.XSSFWorkbook） 直到调用 Workbook#write() 之前在内存存活着大量的对象。
谷歌一番POI官网提供了org.apache.poi.xssf.streaming.SXSSFWorkbook 来解决OOM的问题。官方旧的解决方式([Link](https://svn.apache.org/repos/asf/poi/trunk/src/examples/src/org/apache/poi/xssf/usermodel/examples/BigGridDemo.java))也能使用，不过已经被集合到 SXSSFWorkbook 中。但官网是不提供一边查数据，一边以Stream的方式输出Excel。根据**01定律**，理论上是可以做到，最差也就手写0和1:)

## 例子
根据官网文档，可以在看到 SXSSFWorkbook 其实是将数据刷到本地的硬盘上，实现了自动刷入和手动输入，
最后 write() 的时候安全输出而不会导出OOM。

我们来看看官方例子怎么样
```
Workbook workbook = new SXSSFWorkbook();// 1
Sheet sheet = workbook.createSheet();//2
CellStyle cellStyle = workbook.createCellStyle();//4
    for (int i = 0; i < 60000; i++) {
        Row newRow = sheet.createRow(i);//3
        for (int j = 0; j < 100; j++) {
            newRow.createCell(j).setCellValue("test" + Math.random());
			newRow.setCllStyle(cellStyle);//3
        }
    }
ByteArrayOutputStream os = new ByteArrayOutputStream();
workbook.write(os);//4
```

### 0x01 Workbook
``` new SXSSFWorkbook() ``` 是创建一个刷新数据的窗口大小（rowAccessWindowSize）为100的 SXSSFWorkbook。在内存里只允许多少个行对象存在（下面会说明）。如果是由于列太多导致OOM，官方提供的方案是解决不了的^_^ ([技术指标](https://zh.wikipedia.org/wiki/Microsoft_Excel#%E6%8A%80%E6%9C%AF%E6%8C%87%E6%A0%87)) 可以看出SXSSFWorkbook默认是包装了一下XSSFWorkbook，_wb 实际指向的是 XSSFWorkbook。
```
public static final int DEFAULT_WINDOW_SIZE = 100;

public SXSSFWorkbook(){
	this(null /*workbook*/);
}

public SXSSFWorkbook(XSSFWorkbook workbook){
	this(workbook, DEFAULT_WINDOW_SIZE);
}

//最终调的是这个方法
public SXSSFWorkbook(XSSFWorkbook workbook, int rowAccessWindowSize, boolean compressTmpFiles, boolean useSharedStringsTable){
    setRandomAccessWindowSize(rowAccessWindowSize);
    setCompressTempFiles(compressTmpFiles);
    if (workbook == null) {
        _wb=new XSSFWorkbook();//实际存的是 XSSFWorkbook
        _sharedStringSource = useSharedStringsTable ? _wb.getSharedStringSource() : null;
    } else {
        _wb=workbook;
        _sharedStringSource = useSharedStringsTable ? _wb.getSharedStringSource() : null;
        for ( Sheet sheet : _wb ) {
            createAndRegisterSXSSFSheet( (XSSFSheet)sheet );
        }
    }
}
```

### 0x02 Sheet
``` workbook.createSheet() ```创建一个 SXSSFSheet 实际也是包装了一个 XSSFSheet，SXSSFSheet 很多方法其实是调用了 XSSFSheet的。
```
public SXSSFSheet createSheet()
{
    return createAndRegisterSXSSFSheet(_wb.createSheet());
}

SXSSFSheet createAndRegisterSXSSFSheet(XSSFSheet xSheet)
{
    final SXSSFSheet sxSheet;
    try
    {
        sxSheet=new SXSSFSheet(this,xSheet);
    }
    catch (IOException ioe)
    {
        throw new RuntimeException(ioe);
    }
    registerSheetMapping(sxSheet,xSheet);
    return sxSheet;
}
```
```new SXSSFSheet(this,xSheet)```会创建一个```SheetDataWriter```对象，从名称可以看到是一个Sheet的数据输出对象，追踪到里面可以得知创建一个```SheetDataWriter```对象实际时创建了一个前缀为poi-sxssf-sheet的xml文件和这文件对应的java.io.Writer。xml文件的路径是在 %java.io.tmpdir%/poifiles 下 

```
public SheetDataWriter() throws IOException {
    _fd = createTempFile();
    _out = createWriter(_fd);
}

public File createTempFile() throws IOException {
    return TempFile.createTempFile("poi-sxssf-sheet", ".xml");
}
```
### 0x03 Row
createRow利用上面的sheet来新建行对象保存到内存中，如果在内存的对象超出 _randomAccessWindowSize 就用 SheetDataWriter刷新到硬盘上的临时xml文件里。至于 Cell 也是保存在 Row 对象的 _cells 字段里面。在刷新数据到硬盘上是使用了上面创建的 SheetDataWriter 对象，```writeRow(int,SXSSFRow)```构建成行的xml格式将数据追加到临时文件最后。
```
@Override
public SXSSFRow createRow(int rownum)
{
    int maxrow = SpreadsheetVersion.EXCEL2007.getLastRowIndex();//判断最大行数
    if (rownum < 0 || rownum > maxrow) {
        throw new IllegalArgumentException("Invalid row number (" + rownum
                + ") outside allowable range (0.." + maxrow + ")");
    }

    // attempt to overwrite a row that is already flushed to disk
    // 行数必须大于已经刷到硬盘的行数
    if(rownum <= _writer.getLastFlushedRow() ) {
        throw new IllegalArgumentException(
                "Attempting to write a row["+rownum+"] " +
                "in the range [0," + _writer.getLastFlushedRow() + "] that is already written to disk.");
    }

    // attempt to overwrite a existing row in the input template
    // 行数必须大于模板的行数
    if(_sh.getPhysicalNumberOfRows() > 0 && rownum <= _sh.getLastRowNum() ) {
        throw new IllegalArgumentException(
                "Attempting to write a row["+rownum+"] " +
                        "in the range [0," + _sh.getLastRowNum() + "] that is already written to disk.");
    }

    SXSSFRow newRow=new SXSSFRow(this);//SXSSFRow 保存当前的 SXSSFSheet 对象
    _rows.put(rownum,newRow);// 保存行号和行对象，可以看出如何数据量大就会导致OOM
    allFlushed = false;
    // 在内存的行数是否大于刷新的窗口大小，大于就刷到硬盘的临时文件上
    if(_randomAccessWindowSize>=0&&_rows.size()>_randomAccessWindowSize)
    {
        try
        {
           flushRows(_randomAccessWindowSize);
        }
        catch (IOException ioe)
        {
            throw new RuntimeException(ioe);
        }
    }
    return newRow;
}

private void flushOneRow() throws IOException
    {
        Integer firstRowNum = _rows.firstKey();
        if (firstRowNum!=null) {
            int rowIndex = firstRowNum.intValue();
            SXSSFRow row = _rows.get(firstRowNum);
            // Update the best fit column widths for auto-sizing just before the rows are flushed
            _autoSizeColumnTracker.updateColumnWidths(row);
            _writer.writeRow(rowIndex, row);// 使用了上面创建的 SheetDataWriter 来写到硬盘上
            _rows.remove(firstRowNum);
            lastFlushedRowNumber = rowIndex;
        }
    }

```

### 0x04 WorkBook#write(OutputStream) 和其他
CellStyle等通过 SXSSFWorkbook 创建的对象底层都是通过 XSSFWorkbook 创建。这些对象也是停留在内存中，并不会写到硬盘上。最终输出时
先把内存的 row 全部刷到硬盘上，再把Excel模板刷到硬盘上。这个模板其实就是一个包含style等但不包含数据的excel文件，可以看到这excel文件时通过zip格式写到硬盘上，所以又有了开头说的可以解压看excel里面的内容。

```
public void write(OutputStream stream) throws IOException
{
    flushSheets();//把内存中的所有数据刷到硬盘上

    //Save the template
    File tmplFile = TempFile.createTempFile("poi-sxssf-template", ".xlsx");
    boolean deleted;
    try {
        FileOutputStream os = new FileOutputStream(tmplFile);// 保存 XSSFWorkbook 模板
        try {
            _wb.write(os);
        } finally {
            os.close();
        }

        //Substitute the template entries with the generated sheet data files
        final ZipEntrySource source = new ZipFileZipEntrySource(new ZipFile(tmplFile));
        injectData(source, stream);//往zip文件里面注入数据
    } finally {
        deleted = tmplFile.delete();
    }
    ....省略
}

protected void injectData(ZipEntrySource zipEntrySource, OutputStream out) throws IOException {
    ....省略
	// See bug 56557, we should not inject data into the special ChartSheets
	if(xSheet!=null && !(xSheet instanceof XSSFChartSheet)) {//判断是否 sheet， 是读取xml文件输出，否则直接输出
	    SXSSFSheet sxSheet=getSXSSFSheet(xSheet);
	    InputStream xis = sxSheet.getWorksheetXMLInputStream();
	    try {
	        copyStreamAndInjectWorksheet(is,zos,xis);// 读取 xml 文件再输出
	    } finally {
	        xis.close();
	    }
	} else {
	    IOUtils.copy(is, zos);
	}
	....省略
}

private static void copyStreamAndInjectWorksheet(InputStream in, OutputStream out, InputStream worksheetData) throws IOException {
     ....省略
    //Copy the worksheet data to "out".
    IOUtils.copy(worksheetData,out);// 将 xml 文件的内容复制到输出流中，从而输出到客户端
    
	outWriter.write("</sheetData>");
    outWriter.flush();
    //Copy the rest of "in" to "out".
    while(((c=inReader.read())!=-1)) {
        outWriter.write(c);
    }
    outWriter.flush();
}

```

### 0x05 poi 解决 OOM 总结
poi 把最终 excel 拆分成模板文件和数据分开保存，数据保存到硬盘上，模板（包含Style、字体等信息）保存在内存上。输出时把模板生成为 xlsx 文件再以 zip 格式读回出来重新输出给客服端，如果读到 sheet 的文件就替换成 xml 数据文件输出。由于 zip 只是一个打包，并没有压缩混乱了整个文件，可以看作一个把一个文件夹的内容输出。


### 0x06 修改
其实修改方式有很多，可以由模板到数据把整个输出都处理了，这是最完美的做法。但这方法需要修改的地方太多，需要了解的内容也太多。由于时间的关系，所以我就采用继承 SXSSFWorkbook 在注入数据时不从文件中读取，改为即时读取业务数据即时生成 xml 数据文件。把生成 xml 文件时的 SheetDataWriter#_out 通过反射修改成指 OutputStream 即可解决把输出重定向。 这样可以复用 poi 原生的内容而又不用改动太大。由于```copyStreamAndInjectWorksheet```是私有方法不能重写，那只能复制源码并重写```injectData```方法；```injectData```也调了私有方法，可以用反射来解决。

```
public class StreamSXSSFWorkbook extends SXSSFWorkbook {

    /**
     * 消费数据,生成Row
     */
    private Consumer<Sheet> sheetConsumer;
	


	private static void copyStreamAndInjectWorksheet(InputStream in, OutputStream out, InputStream worksheetData) throws IOException {
	     ....省略
	    //Copy the worksheet data to "out".
        //IOUtils.copy(worksheetData,out);// 将 xml 文件的内容复制到输出流中，从而输出到客户端
        //将生成 xml 数据文件的输出流改为输出到客户端的输出流
        try {
            Field writerField = findField(sheet.getClass(), "_writer");
            writerField.setAccessible(true);
            SheetDataWriter sheetDataWriter = (SheetDataWriter) writerField.get(sheet);

            Field outField = findField(sheetDataWriter.getClass(), "_out");
            outField.setAccessible(true);
            outField.set(sheetDataWriter, outWriter);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        consumer.accept(sheet);//生成并输出 xml 数据文件
        sheet.flushRows();//刷新内存中（rowAccessWindowSize控制的）剩下的数据

        outWriter.write("</sheetData>");
        outWriter.flush();
        //Copy the rest of "in" to "out".
        while (((c = inReader.read()) != -1)) {
            outWriter.write(c);
        }
        outWriter.flush();
	}

	public void setSheetConsumer(Consumer<Sheet> sheetConsumer) {
        this.sheetConsumer = sheetConsumer;
    }
}
```
使用方法
```
StreamSXSSFWorkbook wb = new StreamSXSSFWorkbook(1000);
	List<CellStyle> cellStyles = initCellStyle(wb);//必须先创建
	wb.setSheetConsumer(sheet -> {
		List<String> ls = data();
		
		Iterator<String> it = ls.iterator();
        int i = 0;
        while (it.hasNext()){
            String val = it.next();
			
			CellStyle style = cellStyles.get(i);
	        Row row = sheet.createRow(i);
	        Cell cell = row.createCell(0);
	        cell.setCellStyle(style);
			cell.setCellValue(val);

            it.remove();
            i++;
       }
});
wb.write(out);
```

### 0x07 限制、优化、建议
1. 我使用的版本时 poi 3.17，修改的```copyStreamAndInjectWorksheet```方法和```injectData```方法依赖于源码，升级版本时源码修改了也需要做相应的修改
2. 由于生成 excel 时是先生成输出模板在注入数据，所以CellStyle等通过Workbook创建的对象在setSheetConsumer里生成是没有效果的；但可以在setSheetConsumer之前生成，再传入里面。
3. 上面的方法还是会生成临时文件，只不过临时文件是空的，可以通过重写```SheetDataWriter```来优化。同样道理也是可以不用成模板的临时文件再读出输出，但这样子修改成本比较大，不过也是最完美。
4. 建议输出大量数据时做到分页查询再输入，并且业务数据以pop的方式读完就删除，让JVM尽快回收。