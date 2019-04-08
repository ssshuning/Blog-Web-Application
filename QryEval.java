/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.3.
 */

import java.io.*;
import java.util.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};


    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.


        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));
        RetrievalModel model = initializeRetrievalModel(parameters);
        String queryFilePath = parameters.get("queryFilePath");
        String outputPath = parameters.get("trecEvalOutputPath");
        String outputRange = parameters.get("trecEvalOutputLength");

        //  Perform experiments.
        if(parameters.containsKey("fb")&&parameters.get("fb").equals("true")){
            if(!parameters.containsKey("fbDocs")||
                    !parameters.containsKey("fbTerms")||!parameters.containsKey("fbMu")||!parameters.containsKey("fbOrigWeight")||
                    !parameters.containsKey("fbExpansionQueryFile")){
                throw new IllegalArgumentException
                        ("Required parameters were missing from the parameter file.");
            }else{
                int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
                int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
                double fbMu = Double.parseDouble(parameters.get("fbMu"));
                double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
                String qryFile = parameters.get("fbExpansionQueryFile");

                if(parameters.containsKey("fbInitialRankingFile")){
                    String rankingFile = parameters.get("fbInitialRankingFile");
                    Map<Integer,ScoreList> scoreList = readRankingFile(rankingFile);
                    processExpandedQuery(fbOrigWeight,qryFile,queryFilePath,outputPath,outputRange,model,fbDocs,fbTerms,fbMu,scoreList);
                }else{
                    processExpandedQuery(fbOrigWeight,qryFile,queryFilePath,outputPath,outputRange,model,fbDocs,fbTerms,fbMu,null);
                }

            }
        }else if(parameters.containsKey("retrievalAlgorithm")&&parameters.get("retrievalAlgorithm").equals("letor")){
            ((RetrievalLeToRModel)model).execute();
        }
        else{
            processQueryFile(queryFilePath, outputPath,outputRange,model);
        }



        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if(modelString.equals("bm25")) {
            if (!(parameters.containsKey("BM25:k_1") &&
                    parameters.containsKey("BM25:b") &&
                    parameters.containsKey("BM25:k_3"))) {
                throw new IllegalArgumentException
                        ("Required parameters were missing from the parameter file.");
            }else{
                double k_1 = 0.0;
                double b = 0.0;
                double k_3 = 0.0;
                if(parameters.containsKey("BM25:k_1")){
                    k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
                }
                if(parameters.containsKey("BM25:b")){
                    b = Double.parseDouble(parameters.get("BM25:b"));
                }
                if(parameters.containsKey("BM25:k_3")) {
                    k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
                }
                model = new RetrievalModelBM25(k_1,b,k_3);
            }

        }else if(modelString.equals("indri")){
            if (!(parameters.containsKey("Indri:mu") &&
                    parameters.containsKey("Indri:lambda"))){
            throw new IllegalArgumentException
                        ("Required parameters were missing from the parameter file.");
            }else {
                double mu = 0.0;
                double lambda = 0.0;
                if(parameters.containsKey("Indri:mu")){
                    mu = Double.parseDouble(parameters.get("Indri:mu"));
                }
                if(parameters.containsKey("Indri:lambda")){
                    lambda = Double.parseDouble(parameters.get("Indri:lambda"));
                }

                model = new RetrievalModelIntri(mu,lambda);
            }
        }else if(modelString.equals("letor")){
            if (!(parameters.containsKey("BM25:k_1") &&
                    parameters.containsKey("BM25:b") &&
                    parameters.containsKey("BM25:k_3"))) {
                throw new IllegalArgumentException
                        ("Required parameters were missing from the parameter file.");
            }
            if (!(parameters.containsKey("Indri:mu") &&
                    parameters.containsKey("Indri:lambda"))) {
                throw new IllegalArgumentException
                        ("Required parameters were missing from the parameter file.");
            }

            model = new RetrievalLeToRModel(parameters);

        }else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }
        return model;
    }

    private static Map<Integer,ScoreList> readRankingFile(String rankingFile) throws IOException {
        Map<Integer,ScoreList> map = new HashMap<>();
        BufferedReader input = null;
        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(rankingFile));

         //   PrintWriter writer = new PrintWriter(rankingFile, "UTF-8");
            while ((qLine = input.readLine()) != null) {
                String[] info = qLine.split(" +");
                int qryNo = Integer.parseInt(info[0].trim());
                int docNo = Idx.getInternalDocid(info[2].trim());
                Double score = Double.parseDouble(info[4].trim());
                if(!map.containsKey(qryNo)){
                    ScoreList scoreList = new ScoreList();
                    scoreList.add(docNo,score);
                    map.put(qryNo,scoreList);
                }else{
                    map.get(qryNo).add(docNo,score);
                }

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }


    private static PriorityQueue<Map.Entry<String,Double>> expandQuery(int fbDocs,int fbTerms,double fbMu,ScoreList scoreList) throws IOException {
        //p(t|d)*p(I|d)log(length(C)/ctf)
//        int docNum = Math.min(fbDocs,scoreList.size());
        Map<String,Long> ctfCollection = new HashMap<>();
        for(int i = 0;i<fbDocs;i++){
            TermVector tv = new TermVector(scoreList.getDocid(i),"body");
            for(int j = 1;j<tv.stemsLength();j++){
                ctfCollection.put(tv.stemString(j),tv.totalStemFreq(j));
            }
        }
        Map<String,Double> termMap = new HashMap<>();
        for(int i = 0;i<fbDocs;i++){
            int docId = scoreList.getDocid(i);
            TermVector termVector = new TermVector(docId,"body");
            for(Map.Entry<String,Long> termEntry:ctfCollection.entrySet()){
                String term = termEntry.getKey();
                if (term.contains(".") || term.contains(",")) {
                    continue;
                }

                long ctf = termEntry.getValue();
                double totalScore = 0.0;
                int docLen = termVector.positionsLength();
                long toalLen = Idx.getSumOfFieldLengths("body");
                int tf = 0;
                if (termVector.indexOfStem(term) != -1) {
                    tf = termVector.stemFreq(termVector.indexOfStem(term));
                }
                double probC = ((double) ctf) / toalLen;
//                System.out.println("this is probC " + probC);

                double probT = (tf + fbMu * probC) / (docLen + fbMu);
                double idf = Math.log(1 / probC);
                double orgScore = scoreList.getDocidScore(i);
                double newScore = probT * orgScore * idf;
                if (termMap.containsKey(term)) {
                    totalScore = termMap.get(term);
                }
                totalScore += newScore;
                termMap.put(term, totalScore);
                //  System.out.println(termMap.getOrDefault(term,0.0));

            }
        }

        PriorityQueue<Map.Entry<String,Double>> pq  = new PriorityQueue<>((a,b)->a.getValue().compareTo(b.getValue()));
        for(Map.Entry<String,Double> entry:termMap.entrySet()){
            pq.add(entry);
            if(pq.size()>fbTerms){
//                System.out.println(pq.peek().getKey()+" "+pq.peek().getValue()+" testing");
                pq.poll();
            }

        }
        return pq;
    }

    private static void processExpandedQuery(double fbOrigWeight,String qryFile, String queryFilePath,String outputPath,
                                             String outputRange,RetrievalModel model, int fbDocs,int fbTerms,
                                             double fbMu,Map<Integer,ScoreList> scoreList)
            throws IOException{
        BufferedReader input = null;
        PrintWriter qryWriter = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));
            qryWriter = new PrintWriter(qryFile);

            //  Each pass of the loop processes one query.
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");
            while ((qLine = input.readLine()) != null) {


                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;

                if(scoreList!=null&&scoreList.containsKey(qid)){
                    r = scoreList.get(qid);
                }else{
                    r = processQuery(query,model);
                }
                r.sort();
                PriorityQueue<Map.Entry<String,Double>> pq = expandQuery(fbDocs,fbTerms,fbMu,r);
                StringBuilder expandedQry = new StringBuilder();
                expandedQry.append("#wand(");
                //one expanded query and then write it into the file
                for (int i = 0; i < fbTerms; i++) {
                    Map.Entry entry = pq.poll();
                    String learnedQuery = String.format(" %f %s", entry.getValue(), entry.getKey());
                    expandedQry.append(learnedQuery);
                }
                expandedQry.append(")");
               // System.out.println(expandedQry.toString()+" testing");
                qryWriter.write(qid + ": " + expandedQry.toString() + "\n");
                String newQuery = "#wand(" + String.valueOf(fbOrigWeight) + " " + query + " "
                        + String.valueOf(1 - fbOrigWeight) + " " + expandedQry.toString() + ")";
                r = processQuery(newQuery,model);


                if (r != null) {

                    printResults(qid, outputRange,r,writer);

                    System.out.println();
                }
            }
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            if(qryWriter!=null){
                qryWriter.close();
            }

        }

    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }

            return r;
        } else
            return null;
    }

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static Map<String,List<Integer>> processQueryFile(String queryFilePath, String outputPath,String outputRange,
                                 RetrievalModel model)
            throws IOException {

        BufferedReader input = null;
        Map<String,List<Integer>> qryDocs = new HashMap<>();
        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");
            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);
                List<Integer> docs = new ArrayList<>();

                System.out.println("Query " + qLine);

                ScoreList r = null;

                r = processQuery(query, model);
                r.sort();//adding sort here
                int size = Math.min(Integer.parseInt(outputRange),r.size());
                for (int i = 0; i < size; i++) {
                    docs.add(r.getDocid(i));
                }

                if (r != null) {

                    printResults(qid, outputRange,r,writer);

                    System.out.println();
                }
                qryDocs.put(qid,docs);
            }
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();

        }
        return qryDocs;
    }

    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName,String outputRange, ScoreList result,PrintWriter writer) throws IOException {

//        System.out.println(queryName + ":  ");
        if (result.size() < 1||result==null) {
            writer.println(queryName + " Q0 " + "dummy" + " 1 "
                    + "0" + " HW");
        } else {
            result.sort();//shuning
            int size = Math.min(Integer.parseInt(outputRange),result.size());
            for (int i = 0; i < size; i++) {
                writer.println(queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i + 1) + " "
                        + result.getDocidScore(i) + " HW");
            }
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath") &&
                parameters.containsKey("retrievalAlgorithm"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

}
