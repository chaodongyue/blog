import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SheetDataWriter;
import org.apache.poi.xssf.usermodel.XSSFChartSheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 修改了 {@link SXSSFWorkbook#copyStreamAndInjectWorksheet(InputStream, OutputStream, InputStream)} 的合并输出数据方式。
 * <p>
 * 原来的逻辑是在本地生成数据xml文件和模版excel文件,再重新读取这些文件然后合并输出
 * <br/>
 * 改为只在本地生成模版,在合并模版数据输出时不读取数据xml,而是即时生成数据xml
 * <p>
 * 使用限制: 使用样式(CellStyle) 时需要先新建到Workbook, 在<code>sheetConsumer</code>的sheet里通过引用CellStyle对象来使用样式
 * <pre>
 * {@code
 * 依赖POI
 * <dependency>
 *   <groupId>org.apache.poi</groupId>
 *   <artifactId>poi-ooxml</artifactId>
 *   <version>3.17</version>
 * </dependency>
 * }
 * </pre>
 * <pre>
 * {@code
 *  //使用例子
 *  StreamSXSSFWorkbook wb = new StreamSXSSFWorkbook(1000);
 *  List<CellStyle> cellStyles = initCellStyle(wb);//必须先创建
 *  wb.setSheetConsumer(sheet -> {
 *      for(int i = 0;i < 10;i++){
 *          CellStyle style = cellStyles.get(i);
 *          Row row = sheet.createRow(i);
 *          Cell cell = row.createCell(0);
 *          cell.setCellStyle(style);
 *      }
 *  });
 *  wb.write(out);
 * }
 * </pre>
 * <p>
 * TODO new StreamSXSSFWorkbook时还是会创建xml数据文件,只是文件为空,可以优化成xml数据文件也不创建,需要重写SXSSFSheet
 */
public class StreamSXSSFWorkbook extends SXSSFWorkbook {

    /**
     * 消费数据,生成Row
     */
    private Consumer<Sheet> sheetConsumer;


    public StreamSXSSFWorkbook() {
        super();
    }

    public StreamSXSSFWorkbook(XSSFWorkbook workbook) {
        super(workbook);
    }

    public StreamSXSSFWorkbook(XSSFWorkbook workbook, int rowAccessWindowSize) {
        super(workbook, rowAccessWindowSize);
    }

    public StreamSXSSFWorkbook(XSSFWorkbook workbook, int rowAccessWindowSize, boolean compressTmpFiles) {
        super(workbook, rowAccessWindowSize, compressTmpFiles);
    }

    public StreamSXSSFWorkbook(XSSFWorkbook workbook, int rowAccessWindowSize, boolean compressTmpFiles,
                               boolean useSharedStringsTable) {
        super(workbook, rowAccessWindowSize, compressTmpFiles, useSharedStringsTable);
    }

    public StreamSXSSFWorkbook(int rowAccessWindowSize) {
        super(rowAccessWindowSize);
    }

    @Override
    protected void injectData(ZipEntrySource zipEntrySource, OutputStream out) throws IOException {
        Method getSheetFromZipEntryNameMethod = findMethod(SXSSFWorkbook.class,
                "getSheetFromZipEntryName", String.class);
        getSheetFromZipEntryNameMethod.setAccessible(true);

        Method getSXSSFSheetMethod = findMethod(SXSSFWorkbook.class,
                "getSXSSFSheet", XSSFSheet.class);
        getSXSSFSheetMethod.setAccessible(true);


        try {
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                Enumeration<? extends ZipEntry> en = zipEntrySource.getEntries();
                while (en.hasMoreElements()) {
                    ZipEntry ze = en.nextElement();
                    zos.putNextEntry(new ZipEntry(ze.getName()));
                    try (InputStream is = zipEntrySource.getInputStream(ze)) {
                        XSSFSheet xSheet = (XSSFSheet) getSheetFromZipEntryNameMethod.invoke(this, ze.getName());
                        // See bug 56557, we should not inject data into the special ChartSheets
                        if (xSheet != null && !(xSheet instanceof XSSFChartSheet)) {
                            SXSSFSheet sxSheet = (SXSSFSheet) getSXSSFSheetMethod.invoke(this, xSheet);
                            copyStreamAndInjectWorksheet(is, zos, sxSheet, sheetConsumer);
                        } else {
                            IOUtils.copy(is, zos);
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } finally {
            zipEntrySource.close();
        }
    }

    private void copyStreamAndInjectWorksheet(InputStream in, OutputStream out, SXSSFSheet sheet
            , Consumer<Sheet> consumer) throws IOException {
        InputStreamReader inReader = new InputStreamReader(in, StandardCharsets.UTF_8);
        OutputStreamWriter outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        boolean needsStartTag = true;
        int c;
        int pos = 0;
        String s = "<sheetData";
        int n = s.length();
        //Copy from "in" to "out" up to the string "<sheetData/>" or "</sheetData>" (excluding).
        while (((c = inReader.read()) != -1)) {
            if (c == s.charAt(pos)) {
                pos++;
                if (pos == n) {
                    if ("<sheetData".equals(s)) {
                        c = inReader.read();
                        if (c == -1) {
                            outWriter.write(s);
                            break;
                        }
                        if (c == '>') {
                            // Found <sheetData>
                            outWriter.write(s);
                            outWriter.write(c);
                            s = "</sheetData>";
                            n = s.length();
                            pos = 0;
                            needsStartTag = false;
                            continue;
                        }
                        if (c == '/') {
                            // Found <sheetData/
                            c = inReader.read();
                            if (c == -1) {
                                outWriter.write(s);
                                break;
                            }
                            if (c == '>') {
                                // Found <sheetData/>
                                break;
                            }

                            outWriter.write(s);
                            outWriter.write('/');
                            outWriter.write(c);
                            pos = 0;
                            continue;
                        }

                        outWriter.write(s);
                        outWriter.write('/');
                        outWriter.write(c);
                        pos = 0;
                        continue;
                    } else {
                        // Found </sheetData>
                        break;
                    }
                }
            } else {
                if (pos > 0) {
                    outWriter.write(s, 0, pos);
                }
                if (c == s.charAt(0)) {
                    pos = 1;
                } else {
                    outWriter.write(c);
                    pos = 0;
                }
            }
        }
        outWriter.flush();
        if (needsStartTag) {
            outWriter.write("<sheetData>\n");
            outWriter.flush();
        }
        //Copy the worksheet data to "out".
        //只修改了注入数据的逻辑
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
        consumer.accept(sheet);
        sheet.flushRows();

        outWriter.write("</sheetData>");
        outWriter.flush();
        //Copy the rest of "in" to "out".
        while (((c = inReader.read()) != -1)) {
            outWriter.write(c);
        }
        outWriter.flush();
    }

    /**
     * 反射获取方法,实现方式可采用多种方式
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return ReflectionUtils.findMethod(clazz, name, paramTypes);
    }

    /**
     * 反射获取字段,实现方式可采用多种方式
     */
    private Field findField(Class<?> clazz, String name) {
        return ReflectionUtils.findField(clazz, name);
    }

    public void setSheetConsumer(Consumer<Sheet> sheetConsumer) {
        this.sheetConsumer = sheetConsumer;
    }
}
