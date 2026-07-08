import org.libreoffice.eurostat.addin.EurostatAddin;

/**
 * Calls the add-in's core logic directly, in-process, with no soffice/UNO bridge involved --
 * _EurostatAddin's constructor doesn't touch any live UNO service and the HTTP fetch uses
 * plain HttpURLConnection, so this is a valid smoke test of the fetch+parse+table logic
 * without needing a running LibreOffice instance.
 */
public class SmokeTest {
    public static void main(String[] args) throws Exception {
        EurostatAddin._EurostatAddin addin = new EurostatAddin._EurostatAddin(null);

        System.out.println("Calling EUROSTATDATA(\"tec00114\", \"geo=DE;time=2023\") ...");
        Object[][] table = addin.getEurostatData("tec00114", "geo=DE;time=2023");
        printTable(table);

        if (table.length == 1 && table[0].length == 1 && String.valueOf(table[0][0]).startsWith("#ERROR")) {
            throw new AssertionError("Expected real data rows, got an error: " + table[0][0]);
        }
        if (table.length < 2) {
            throw new AssertionError("Expected a header row plus at least one observation row");
        }

        System.out.println();
        System.out.println("Calling EUROSTATDATA(\"does_not_exist_xyz\", \"\") to check error handling ...");
        Object[][] errorTable = addin.getEurostatData("does_not_exist_xyz", "");
        printTable(errorTable);
        if (!(errorTable.length == 1 && String.valueOf(errorTable[0][0]).startsWith("#ERROR"))) {
            throw new AssertionError("Expected a single #ERROR cell for an invalid dataset code");
        }

        System.out.println();
        System.out.println("SMOKE TEST PASSED");
    }

    private static void printTable(Object[][] table) {
        for (Object[] row : table) {
            StringBuilder sb = new StringBuilder();
            for (Object cell : row) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(cell);
            }
            System.out.println(sb);
        }
    }
}
