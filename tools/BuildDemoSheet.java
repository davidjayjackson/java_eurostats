import com.sun.star.awt.FontWeight;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.XPropertySet;
import com.sun.star.bridge.XUnoUrlResolver;
import com.sun.star.container.XIndexAccess;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XStorable;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.sheet.XArrayFormulaRange;
import com.sun.star.sheet.XCalculatable;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.table.XCellRange;
import com.sun.star.table.XColumnRowRange;
import com.sun.star.text.XText;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

/**
 * Generates demo/Eurostat-Demo.ods: a real Calc document with several EUROSTATDATA(...) array
 * formulas already entered and computed, so opening the file shows the add-in working without
 * needing to type anything. Run against a headless soffice that already has EurostatAddin.oxt
 * installed (see run-demo.sh).
 */
public class BuildDemoSheet {
    public static void main(String[] args) throws Exception {
        String connectString = args.length > 0 ? args[0]
                : "uno:socket,host=localhost,port=2083;urp;StarOffice.ComponentContext";
        String outputPath = args.length > 1 ? args[1] : "demo/Eurostat-Demo.ods";

        XComponentContext ctx = connectWithRetry(connectString, 30);
        XMultiComponentFactory smgr = ctx.getServiceManager();

        Object desktopObj = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx);
        XComponentLoader loader = UnoRuntime.queryInterface(XComponentLoader.class, desktopObj);

        PropertyValue hidden = new PropertyValue();
        hidden.Name = "Hidden";
        hidden.Value = Boolean.TRUE;

        XComponent comp = loader.loadComponentFromURL(
                "private:factory/scalc", "_blank", 0, new PropertyValue[] { hidden });
        XSpreadsheetDocument doc = UnoRuntime.queryInterface(XSpreadsheetDocument.class, comp);

        XIndexAccess sheetsIA = UnoRuntime.queryInterface(XIndexAccess.class, doc.getSheets());
        XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, sheetsIA.getByIndex(0));

        setBoldText(sheet, "A1", "Eurostat Data for Calc — Demo");
        setText(sheet, "A2", "Live data pulled by the EUROSTATDATA() add-in function. Edit the formulas below and recalculate (F9) to try your own dataset codes / filters.");

        setBoldText(sheet, "A4", "GDP per capita (PPS), Germany 2023 — tec00114");
        arrayFormula(sheet, "A5:F6", "=EUROSTATDATA(\"tec00114\";\"geo=DE;time=2023\")");

        setBoldText(sheet, "A8", "Unemployment rate by sex, France 2023 — une_rt_a");
        arrayFormula(sheet, "A9:G12", "=EUROSTATDATA(\"une_rt_a\";\"geo=FR;time=2023;unit=PC_ACT;age=Y15-74\")");

        setBoldText(sheet, "A14", "Try it yourself — same dataset, Germany:");
        arrayFormula(sheet, "A15:G18", "=EUROSTATDATA(\"une_rt_a\";\"geo=DE;time=2023;unit=PC_ACT;age=Y15-74\")");

        setBoldText(sheet, "A20", "Inflation rate (HICP) by year, Germany, 2015– — tec00118");
        setText(sheet, "A21", "\"sinceTimePeriod\" isn't a dimension — it's one of Eurostat's own reserved query parameters (also: lastTimePeriod) — and works here too, since filters pass straight through to the API.");
        arrayFormula(sheet, "A22:F33", "=EUROSTATDATA(\"tec00118\";\"geo=DE;sinceTimePeriod=2015\")");

        setBoldText(sheet, "A35", "Unknown dataset code — shows a readable error instead of Err:508:");
        arrayFormula(sheet, "A36:A36", "=EUROSTATDATA(\"not_a_real_dataset\";\"\")");

        XCalculatable calculatable = UnoRuntime.queryInterface(XCalculatable.class, doc);
        calculatable.calculateAll();

        autofitColumns(sheet, 0, 9);

        System.out.println("Saving to " + outputPath + " ...");
        java.io.File outFile = new java.io.File(outputPath).getAbsoluteFile();
        String url = outFile.toURI().toString();
        XStorable storable = UnoRuntime.queryInterface(XStorable.class, doc);
        PropertyValue filter = new PropertyValue();
        filter.Name = "FilterName";
        filter.Value = "calc8";
        storable.storeToURL(url, new PropertyValue[] { filter });

        XDesktop desktop = UnoRuntime.queryInterface(XDesktop.class, desktopObj);
        comp.dispose();
        desktop.terminate();

        System.out.println("Done: " + outFile);
    }

    private static void setText(XSpreadsheet sheet, String cellName, String text) throws Exception {
        XCellRange range = sheet.getCellRangeByName(cellName);
        XText xText = UnoRuntime.queryInterface(XText.class, range.getCellByPosition(0, 0));
        xText.setString(text);
    }

    private static void setBoldText(XSpreadsheet sheet, String cellName, String text) throws Exception {
        setText(sheet, cellName, text);
        XCellRange range = sheet.getCellRangeByName(cellName);
        XPropertySet props = UnoRuntime.queryInterface(
                XPropertySet.class, range.getCellByPosition(0, 0));
        props.setPropertyValue("CharWeight", Float.valueOf(FontWeight.BOLD));
    }

    private static void arrayFormula(XSpreadsheet sheet, String rangeName, String formula) throws Exception {
        XCellRange range = sheet.getCellRangeByName(rangeName);
        XArrayFormulaRange arrayRange = UnoRuntime.queryInterface(XArrayFormulaRange.class, range);
        arrayRange.setArrayFormula(formula);
    }

    private static void autofitColumns(XSpreadsheet sheet, int firstCol, int lastCol) throws Exception {
        XColumnRowRange columnRowRange = UnoRuntime.queryInterface(XColumnRowRange.class, sheet);
        XIndexAccess columns = columnRowRange.getColumns();
        for (int c = firstCol; c <= lastCol; c++) {
            XPropertySet colProps = UnoRuntime.queryInterface(XPropertySet.class, columns.getByIndex(c));
            colProps.setPropertyValue("OptimalWidth", Boolean.TRUE);
        }
    }

    private static XComponentContext connectWithRetry(String connectString, int attempts) throws Exception {
        XComponentContext localContext = com.sun.star.comp.helper.Bootstrap.createInitialComponentContext(null);
        XMultiComponentFactory localFactory = localContext.getServiceManager();
        Object resolverObj = localFactory.createInstanceWithContext(
                "com.sun.star.bridge.UnoUrlResolver", localContext);
        XUnoUrlResolver resolver = UnoRuntime.queryInterface(XUnoUrlResolver.class, resolverObj);

        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            try {
                Object initialObject = resolver.resolve(connectString);
                return UnoRuntime.queryInterface(XComponentContext.class, initialObject);
            } catch (Exception e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("Could not connect to soffice after " + attempts + " attempts", lastError);
    }
}
