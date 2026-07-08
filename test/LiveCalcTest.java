import com.sun.star.beans.PropertyValue;
import com.sun.star.bridge.XUnoUrlResolver;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDesktop;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.sheet.XArrayFormulaRange;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.table.XCell;
import com.sun.star.table.XCellRange;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

/**
 * End-to-end check against a soffice instance we started ourselves (see run-live-test.sh),
 * pointed at the same UserInstallation profile the extension was unopkg-installed into.
 * Opens a blank Calc doc, enters =EUROSTATDATA(...) as an array formula, forces
 * recalculation, and reads the spilled results back out of real cells.
 */
public class LiveCalcTest {
    public static void main(String[] args) throws Exception {
        String connectString = args.length > 0 ? args[0]
                : "uno:socket,host=localhost,port=2083;urp;StarOffice.ComponentContext";

        System.out.println("Connecting to soffice: " + connectString);
        XComponentContext ctx = connectWithRetry(connectString, 30);
        XMultiComponentFactory smgr = ctx.getServiceManager();

        Object desktopObj = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx);
        XComponentLoader loader = UnoRuntime.queryInterface(XComponentLoader.class, desktopObj);

        PropertyValue hidden = new PropertyValue();
        hidden.Name = "Hidden";
        hidden.Value = Boolean.TRUE;

        System.out.println("Opening a new Calc document ...");
        XComponent comp = loader.loadComponentFromURL(
                "private:factory/scalc", "_blank", 0, new PropertyValue[] { hidden });
        XSpreadsheetDocument doc = UnoRuntime.queryInterface(XSpreadsheetDocument.class, comp);

        com.sun.star.container.XIndexAccess sheetsIA = UnoRuntime.queryInterface(
                com.sun.star.container.XIndexAccess.class, doc.getSheets());
        XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, sheetsIA.getByIndex(0));

        XCellRange range = sheet.getCellRangeByName("A1:H5");
        XArrayFormulaRange arrayRange = UnoRuntime.queryInterface(XArrayFormulaRange.class, range);

        String formula = "=EUROSTATDATA(\"tec00114\";\"geo=DE;time=2023\")";
        System.out.println("Entering array formula: " + formula);
        arrayRange.setArrayFormula(formula);

        com.sun.star.sheet.XCalculatable calculatable = UnoRuntime.queryInterface(
                com.sun.star.sheet.XCalculatable.class, doc);
        calculatable.calculateAll();

        System.out.println("Reading back A1:F2 ...");
        boolean sawValue = false;
        for (int row = 0; row < 2; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 6; col++) {
                XCell cell = sheet.getCellByPosition(col, row);
                String text = cell.getFormula();
                if (text == null || text.isEmpty()) {
                    text = String.valueOf(cell.getValue());
                }
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(text);
                if (row == 1 && col == 5 && cell.getValue() != 0) {
                    sawValue = true;
                }
            }
            System.out.println(sb);
        }

        XDesktop desktop = UnoRuntime.queryInterface(XDesktop.class, desktopObj);
        comp.dispose();
        desktop.terminate();

        if (!sawValue) {
            throw new AssertionError("Expected a non-zero numeric value in the spilled result range");
        }
        System.out.println();
        System.out.println("LIVE CALC TEST PASSED");
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
