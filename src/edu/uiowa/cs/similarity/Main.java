package edu.uiowa.cs.similarity;

import clojure.core.Vec;
import org.apache.commons.cli.*;

import java.io.*;
import java.text.ParseException;
import java.util.*;


public class Main {
    private static List<List<String>> clean;
    private static List<List<String>> unCleanUnique;

    public static void main(String[] args) throws ParseException {
        long start = System.currentTimeMillis();
        Options options = new Options();
        options.addRequiredOption("f", "file", true, "input file to process");
        options.addOption("h", false, "print this help message");
        options.addOption("clean", false, "Cleaning file");
        options.addOption("s", false, "Prints sentences");
        options.addOption("v", false, "Generates semantic descriptor vector");
        options.addOption("t", true, "Cosine similarity");
        options.addOption("m", true, "More similarity");
        options.addOption("k", true, "K-means");
        options.addOption("j", true, "Use top-j to pick best words from k-means clusters");

        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            e.printStackTrace();
        }

        assert cmd != null;
        String filename = cmd.getOptionValue("f");
        if (!new File(filename).exists()) {
            System.err.println("file does not exist "+filename);
            System.exit(1);
        }else {
            //Clean file input using Cleanup
            System.out.println("Cleaning file...");
            File dirty = new File(filename);
            File stopWords = new File("stopwords.txt");

            long filterStart = System.currentTimeMillis();
            FileFilter sentences = new FileFilter(dirty, stopWords);
            clean = sentences.getCleanAndStemmedWords();
            unCleanUnique = sentences.getDirtyWords();
            long filterStop = System.currentTimeMillis();
            System.out.println("Word filter time: " + (filterStop - filterStart)/1000 + " seconds");

            //Prints cleaned sentences. For debugging only
            if (cmd.hasOption("s")) {
                List<String> s;
                for (int i = 0; i<clean.size(); i++) {
                    s = clean.get(i);
                    System.out.println(s);
                }
                System.out.println("\n");
            }
        }

        if (cmd.hasOption("v")) {
            String vectorBase = cmd.getOptionValue("v");
            System.out.println("Calculating all vectors...\n");
            SimilarityVector vector = new SimilarityVector(clean, unCleanUnique);
            Map<String, Vector> vectors = vector.makeAllVectors();
            Iterator<String> w = vectors.keySet().iterator();

            while (w.hasNext()) {
                vectors.get(w.next()).printVector();
            }
        }

        if (cmd.hasOption("t")) {
            String tmp = cmd.getOptionValue("t");
            String[] a = tmp.split(",");
            String keyword = a[0];
            int num = Integer.parseInt(a[1]);

            System.out.println("Calculating all vectors...\n");
            long startMakeVector = System.currentTimeMillis();
            Vector myVector = new Vector();
            SimilarityVector v = new SimilarityVector(clean, unCleanUnique);
            Map<String, Vector> vectors = v.makeAllVectors();
            long stopMakeVector = System.currentTimeMillis();
            String holyGrail;
            String message;
            Value info;
            int count = 0;
            PriorityQueue<Value> eucOrdered = new PriorityQueue<>(Collections.reverseOrder());

            System.out.println("Vector make time: " + (stopMakeVector - startMakeVector) / 1000 + " seconds");
            System.out.println("There are " + vectors.size() + " unique vectors\n");

            if (cmd.hasOption("m")) {
                long startEuc = System.currentTimeMillis();
                String sim = cmd.getOptionValue("m");
                EuclideanDistance distance = new EuclideanDistance(vectors.get(myVector.cleanWord(keyword)));
                String key;

                if (sim.equalsIgnoreCase("euc")) {
                    System.out.println("The Euclidean distance will be based on the similarity vector of " + keyword);
                    System.out.println("");
                    Iterator<String> eucKeyIterator = vectors.keySet().iterator();
                    holyGrail = distance.getBaseVector().getBase();

                    while (eucKeyIterator.hasNext()) {
                        key = eucKeyIterator.next();
                        if (!key.equalsIgnoreCase(myVector.cleanWord(keyword))){
                            distance.setVectorToCompare(vectors.get(key));
                            message =  ("Euclidean distance of " + holyGrail + " -> ");
                            message += (distance.getVectorToCompare().getBase() + ":  ");
                            info = new Value(distance.getEucDistance(), message);
                            eucOrdered.add(info);
                        }
                    }

                    long stopEuc = System.currentTimeMillis();
                    Value toPrint;
                    while (!eucOrdered.isEmpty() && count<num) {
                        toPrint = eucOrdered.poll();
                        System.out.println(toPrint.getValue() + toPrint.getKey());
                        count++;
                    }
                    System.out.println("\nEuclidean distance time: " + (stopEuc - startEuc)/1000 + " seconds");

//                  Normalized vectors
                } else {
                    System.out.println("The euclidean distance between normalized vectors for " + keyword);
                    System.out.println("");
                    Iterator<String> eucKeyIterator = vectors.keySet().iterator();
                    holyGrail = distance.getBaseVector().getBase();

                    while (eucKeyIterator.hasNext()) {
                        key = eucKeyIterator.next();
                        if (!key.equalsIgnoreCase(myVector.cleanWord(keyword))){
                            distance.setVectorToCompare(vectors.get(key));
                            message =  ("Euclidean distance of " + holyGrail + " -> ");
                            message += (distance.getVectorToCompare().getBase() + ":  ");
                            info = new Value(distance.getNormEucDistance(), message);
                            if (!Double.isNaN(info.getKey())) {
                                eucOrdered.add(info);
                            }
                        }
                    }

                    long stopEuc = System.currentTimeMillis();
                    Value toPrint;
                    while (!eucOrdered.isEmpty() && count<num) {
                        toPrint = eucOrdered.poll();
                        System.out.println(toPrint.getValue() + toPrint.getKey());
                        count++;
                    }
                    System.out.println("\nEuclidean distance time: " + (stopEuc - startEuc)/1000 + " seconds");
                }

            }else {
                long startCosine = System.currentTimeMillis();
                PriorityQueue<Value> ordered = new PriorityQueue<>(Collections.reverseOrder());
                System.out.println("Cosine similarity values will be for words compared to " + keyword);
                CosineSimilarity similarity = new CosineSimilarity();
                similarity.setBaseVector(vectors.remove(myVector.cleanWord(keyword)));
                Iterator<String> cosKeyIterator = vectors.keySet().iterator();
                holyGrail = similarity.getBaseVector().getBase();

                while (cosKeyIterator.hasNext()) {
                    similarity.setVectorToCompare(vectors.get(cosKeyIterator.next()));
                    message =  ("Cosine similarity of " + holyGrail + " -> ");
                    message += (similarity.getVectorToCompare().getBase() + ":  ");
                    info = new Value(similarity.calculateCosineSim(), message);
                    ordered.add(info);
                }

                long stopCosine = System.currentTimeMillis();
                System.out.println("Cosine similarity time: " + (stopCosine - startCosine)/1000 + " seconds");

                Value toPrint;
                while (!ordered.isEmpty() && count<num) {
                    toPrint = ordered.poll();
                    System.out.println(toPrint.getValue() + toPrint.getKey());
                    count++;
                }
            }
        }

        if (cmd.hasOption("k")) {
            String x = cmd.getOptionValue("k");
            String[] y = x.split(",");
            int numClust = Integer.parseInt(y[0]);
            int numIter = Integer.parseInt(y[1]);

            System.out.println("Calculating all vectors...\n");
            SimilarityVector vector = new SimilarityVector(clean, unCleanUnique);
            Map<String, Vector> vectors = vector.makeAllVectors();
            System.out.println(vectors.size() + " vectors");
            List<String> keys = new ArrayList<>(vectors.keySet());
            String randomKey;
            List<Vector> centroids = new ArrayList<>();
            Random random = new Random();

            for (int i = 0; i<numClust; i++) {
                randomKey = keys.get(random.nextInt(keys.size()));
                centroids.add(vectors.remove(randomKey));
                System.out.println("Centroid " + i + ": " + centroids.get(i).getBase() + "--> " + centroids.get(i).vectorToList());
            }

            KMeans kmeans = new KMeans(centroids, clean);

            getKmeans(numIter, vectors, kmeans);

        }

        if (cmd.hasOption("j")) {
            String x = cmd.getOptionValue("j");
            String[] y = x.split(",");
            int numClust = Integer.parseInt(y[0]);
            int numIter = Integer.parseInt(y[1]);
            int numResults = Integer.parseInt(y[2]);

            System.out.println("Calculating all vectors...\n");
            SimilarityVector vector = new SimilarityVector(clean, unCleanUnique);
            long startMakeVector = System.currentTimeMillis();
            Map<String, Vector> vectors = vector.makeAllVectors();
            long stopMakeVector = System.currentTimeMillis();
            System.out.println(vectors.size() + " vectors");
            System.out.println("Vector make time: " + (stopMakeVector - startMakeVector) / 1000 + " seconds");
            List<String> keys = new ArrayList<>(vectors.keySet());
            String randomKey;

            List<Vector> centroids = new ArrayList<>();
            Random random = new Random();
            List<String> centroidKeys = new ArrayList<>();

            //Pick random initial centroids from list of vectors
            for (int i = 0; i<numClust; i++) {
                randomKey = keys.get(random.nextInt(keys.size()));
                while (centroidKeys.contains(randomKey)) {
                    randomKey = keys.get(random.nextInt(keys.size()));
                }
                System.out.println("Centroid ->" + randomKey);
                centroids.add(vectors.remove(randomKey));
                centroidKeys.add(randomKey);
            }

            KMeans kmeans = new KMeans(centroids, clean);
            List<List<Vector>> clusters;
            PriorityQueue<Value> ordered = new PriorityQueue<>(Collections.reverseOrder());
            CosineSimilarity similarity = new CosineSimilarity();
            String message;
            Value info;
            List<Vector> currentCluster;
            Vector currentCentroid;

            getKmeans(numIter, vectors, kmeans);
            clusters = kmeans.clusters;
            for (Iterator<List<Vector>> it = clusters.iterator(); it.hasNext(); ) {
                currentCluster = it.next();
                //Iterator<Vector> vecInCluster = currentCluster.iterator();
                for (Iterator<Vector> centIt = centroids.iterator(); centIt.hasNext(); ) {
                    currentCentroid = centIt.next();
                    similarity.setBaseVector(currentCentroid);
                    for (Iterator<Vector> vecInCluster = currentCluster.iterator(); vecInCluster.hasNext(); ){
                        similarity.setVectorToCompare(vecInCluster.next());
                        message = (similarity.getVectorToCompare().getBase() + "  -->  ");
                        info = new Value(similarity.calculateCosineSim(), message);
                        ordered.add(info);
                    }
                }
                int count = 0;
                Value toPrint;
                System.out.println("Current centroid: " + similarity.getBaseVector().vectorToList());
                while (!ordered.isEmpty() && count < numResults) {
                    toPrint = ordered.poll();
                    System.out.println(toPrint.getValue() + toPrint.getKey());
                    count++;
                }
                //Prints if cluster is empty
                if (currentCluster.isEmpty()) {
                    System.out.println("**********************************");
                }
                ordered.clear();
            }
        }

        if (cmd.hasOption("h")) {
            HelpFormatter helpf = new HelpFormatter();
            helpf.printHelp("Main", options, true);
            System.exit(0);
        }
        long stop = System.currentTimeMillis();
        System.out.println("Total execution time: " + (stop - start)/1000 + " seconds");
    }

    private static void getKmeans(int numIter, Map<String, Vector> vectors, KMeans kmeans) {
        for (int z = 0; z<numIter; z++) {
            kmeans.resetClusters();
            System.out.println("---------- Iteration " + (z+1) + " -----------");
            System.out.println("********** ...Clustering... **********");
            kmeans.calcKmeans(vectors);
            System.out.println("Calculating new centroids...");
            kmeans.calcCentroid();
        }
    }

}
