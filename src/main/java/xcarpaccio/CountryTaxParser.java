package xcarpaccio;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CountryTaxParser {


    static Map<String, Double> CountryTaxParser() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("countries.csv"));
        String line = null;

        Map<String, Double> countryTaxes = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] split = line.split(",");
            countryTaxes.put(split[0].trim(), Double.parseDouble(split[1].trim()));
        }

        return countryTaxes;

    }
}
