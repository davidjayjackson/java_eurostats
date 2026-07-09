package org.libreoffice.eurostat.addin;

import com.eurostat.fetcher.client.EurostatApiException;
import com.eurostat.fetcher.client.EurostatClient;
import com.eurostat.fetcher.model.Observation;
import com.eurostat.fetcher.parser.JsonStatParser;
import com.eurostat.fetcher.parser.ParsedDataset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LibreOffice Calc add-in exposing a single spreadsheet function, EUROSTATDATA, that fetches
 * and flattens Eurostat JSON-stat 2.0 data. Structure mirrors the LibreOffice SDK's own
 * ExampleAddIn (sdk/examples/DevelopersGuide/Spreadsheet/ExampleAddIn.java): an outer class
 * providing the passive-registration factory, and a nested implementation class.
 */
public class EurostatAddin {

    public static class _EurostatAddin extends com.sun.star.lib.uno.helper.WeakBase
            implements XEurostatAddin,
                       com.sun.star.lang.XServiceName,
                       com.sun.star.lang.XServiceInfo
    {
        private static final String aServiceName = "org.libreoffice.eurostat.addin.EurostatAddin";
        private static final String aAddInServiceName = "com.sun.star.sheet.AddIn";
        private static final String aImplName = _EurostatAddin.class.getName();

        public _EurostatAddin(com.sun.star.lang.XMultiServiceFactory xFactory) {
        }

        // XEurostatAddin

        public Object[][] getEurostatData(String datasetCode, String filters) {
            try {
                Map<String, String> filterMap = parseFilters(filters);
                EurostatClient client = new EurostatClient();
                Object json = client.fetchDataset(datasetCode, filterMap);
                ParsedDataset dataset = new JsonStatParser().parse(json);
                return toTable(dataset);
            } catch (EurostatApiException e) {
                return errorTable(e.getMessage());
            } catch (RuntimeException e) {
                return errorTable(e.getMessage());
            }
        }

        private static Map<String, String> parseFilters(String filters) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            if (filters == null) {
                return map;
            }
            String trimmed = filters.trim();
            if (trimmed.isEmpty()) {
                return map;
            }
            for (String pair : trimmed.split(";")) {
                String p = pair.trim();
                if (p.isEmpty()) {
                    continue;
                }
                int eq = p.indexOf('=');
                if (eq < 0) {
                    throw new IllegalArgumentException("Invalid filter '" + p + "', expected key=value");
                }
                map.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
            }
            return map;
        }

        private static Object[][] toTable(ParsedDataset dataset) {
            List<String> dimensionIds = dataset.getDimensionIds();
            List<Observation> observations = dataset.getObservations();
            int cols = dimensionIds.size() + 1;
            Object[][] table = new Object[observations.size() + 1][cols];

            for (int c = 0; c < dimensionIds.size(); c++) {
                table[0][c] = dimensionIds.get(c);
            }
            table[0][dimensionIds.size()] = "value";

            for (int r = 0; r < observations.size(); r++) {
                Observation obs = observations.get(r);
                for (int c = 0; c < dimensionIds.size(); c++) {
                    table[r + 1][c] = obs.getDimension(dimensionIds.get(c));
                }
                table[r + 1][dimensionIds.size()] = Double.valueOf(obs.getValue());
            }
            return table;
        }

        private static Object[][] errorTable(String message) {
            return new Object[][] { { "#ERROR: " + message } };
        }

        // XServiceName

        public String getServiceName() {
            return aServiceName;
        }

        // XServiceInfo

        public String getImplementationName() {
            return aImplName;
        }

        public String[] getSupportedServiceNames() {
            return new String[] { aServiceName, aAddInServiceName };
        }

        public boolean supportsService(String aService) {
            return aService.equals(aServiceName) || aService.equals(aAddInServiceName);
        }
    }

    public static com.sun.star.lang.XSingleServiceFactory __getServiceFactory(
            String implName,
            com.sun.star.lang.XMultiServiceFactory multiFactory,
            com.sun.star.registry.XRegistryKey regKey) {
        com.sun.star.lang.XSingleServiceFactory xSingleServiceFactory = null;
        if (implName.equals(_EurostatAddin.aImplName)) {
            xSingleServiceFactory = com.sun.star.comp.loader.FactoryHelper.getServiceFactory(
                    _EurostatAddin.class, _EurostatAddin.aServiceName, multiFactory, regKey);
        }
        return xSingleServiceFactory;
    }
}
