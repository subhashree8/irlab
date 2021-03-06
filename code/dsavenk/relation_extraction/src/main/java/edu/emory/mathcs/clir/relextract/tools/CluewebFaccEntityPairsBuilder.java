package edu.emory.mathcs.clir.relextract.tools;

import edu.stanford.nlp.util.Pair;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by dsavenk on 4/15/14.
 */
public class CluewebFaccEntityPairsBuilder {

    private static Set<String> yaUrl = new HashSet<>();
    private static Map<Pair<String, String>, Integer> pairDist = new HashMap<>();

    public static void processTgzFile(String tgzFileName) throws IOException {
        System.err.println(tgzFileName);
        TarArchiveInputStream tarInput =
                new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(tgzFileName)));

        TarArchiveEntry currentEntry;
        BufferedReader tgzReader = new BufferedReader(new InputStreamReader(tarInput));
        ArrayList<Pair<Integer, String>> entities = new ArrayList<>();
        String lastFilename = "";
        while((currentEntry = tarInput.getNextTarEntry()) != null) {
            if (currentEntry.isFile()) {
                String line;
                while ((line = tgzReader.readLine()) != null) {
                    String[] fields = line.split("\t");
                    String filename = fields[0];
                    if (yaUrl.contains(filename)) {
                        int beginOffset = Integer.parseInt(fields[3]);
                        String mid = fields[7];
                        if (!filename.equals(lastFilename)) {
                            processEntityPairs(entities);
                            lastFilename = filename;
                            entities.clear();
                        }
                        entities.add(new Pair<>(beginOffset, mid));
                    }
                }
            }
        }
        processEntityPairs(entities);

        tarInput.close();
    }

    private static void processEntityPairs(ArrayList<Pair<Integer, String>> entities) {
        for (int i = 0; i < entities.size(); ++i) {
            for (int j = 0; j < entities.size(); ++j) {
                if (i == j ||
                        entities.get(i).second.equals(
                                entities.get(j).second)) continue;
                if (Math.abs(entities.get(i).first - entities.get(j).first) <= 200) {
                    Pair<String, String> pair = new Pair<>(entities.get(i).second, entities.get(j).second);
                    if (!pairDist.containsKey(pair)) {
                        pairDist.put(pair, Math.abs(entities.get(i).first - entities.get(j).first));
                    } else {
                        pairDist.put(pair, Math.min(pairDist.get(pair), Math.abs(entities.get(i).first - entities.get(j).first)));
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            BufferedReader input = new BufferedReader(new FileReader(args[0]));
            String line;
            while ((line = input.readLine()) != null) {
                yaUrl.add(line.split(",")[0]);
            }
            boolean first = true;
            for (String inputFile : args) {
                if (!first)
                    processTgzFile(inputFile);
                first = false;
            }
        } catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
        outputEntityPairs();
    }

    private static void outputEntityPairs() {
        for (Map.Entry<Pair<String, String>, Integer> entityPair : pairDist.entrySet()) {
            System.out.println(entityPair.getKey().first + "\t" + entityPair.getKey().second + "\t" + entityPair.getValue());
        }
    }
}
