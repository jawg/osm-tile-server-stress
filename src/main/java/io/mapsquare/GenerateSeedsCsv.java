package io.mapsquare;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.Random;

// sbt "run-main io.mapsquare.GenerateSeedsCsv"

public class GenerateSeedsCsv {

    public static void main(String[] args) throws IOException {

        Properties prop = new Properties();
        String propFileName = "parameters.properties";

        InputStream inputStream = GenerateSeedsCsv.class.getClassLoader().getResourceAsStream(propFileName);

        prop.load(inputStream);
        int targetSeedCount = Integer.parseInt(prop.getProperty("simulation.users.count"));

        File file = new File("src/test/resources/seeds.csv");
        file.createNewFile();
        PrintWriter writer = new PrintWriter(file);

        Random rand = new Random();

        writer.append("seed");
        writer.append("\n");
        writer.flush();
        for (int i = 0; i < targetSeedCount; i++) {
            int randInt = Math.abs(rand.nextInt());
            writer.append(String.valueOf(randInt));
            writer.append("\n");
            writer.flush();
        }
    }
}
