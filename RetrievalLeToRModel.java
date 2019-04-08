
import java.io.*;
import java.util.*;

/**
 * Created by apple on 30/3/19.
 */
public class RetrievalLeToRModel extends RetrievalModel{
//    private Map<Integer,Map<String,Integer>> relMap;
    private Map<Integer,List<Integer>> qryDocs;
    private List<Integer> featureDisable;
    private double k1;
    private double b;
    private double k3;
    private double lambda;
    private double mu;
    private Map<Integer,Map<Integer,Double>> maxMap;
    private Map<Integer,Map<Integer,Double>> minMap;
//    private List<String> queryStems;
    private String trainingFeatureVectorsFile;
    private String trainingQueryFile;
    private String trainingQrelsFile;
    private double svmRankParamC;
    private String svmRankLearnPath;
    private String svmRankModelFile;
    private String queryFilePath;
    private String testingFeatureVectorsFile;
    private String testingDocumentScores;
    private String svmRankClassifyPath;
    private String trecEvalOutputPath;

    public RetrievalLeToRModel(Map<String,String> parameters){
        this.minMap = new HashMap<>();
        this.maxMap = new HashMap<>();
//        this.queryStems = new ArrayList<>();
        this.k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        this.b = Double.parseDouble(parameters.get("BM25:b"));
        this.k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        this.mu = Double.parseDouble(parameters.get("Indri:mu"));
        this.lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        this.trainingQueryFile = parameters.get("letor:trainingQueryFile");
        this.trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        this.trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        this.trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
        this.featureDisable = new ArrayList<>();
        if(parameters.containsKey("letor:featureDisable")) {
            String[] disabled = parameters.get("letor:featureDisable").split(",");

            for (String s : disabled) {
                System.out.print(s+" ");
                this.featureDisable.add(Integer.parseInt(s));
            }
        }
        this.svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        this.svmRankParamC = Double.parseDouble(parameters.get("letor:svmRankParamC"));
        this.svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        this.svmRankModelFile = parameters.get("letor:svmRankModelFile");
        this.testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        this.testingDocumentScores = parameters.get("letor:testingDocumentScores");
        this.queryFilePath = parameters.get("queryFilePath");
    }
    public void execute() throws Exception {
        Map<Integer,Map<String,Integer>> relMap = null;

        if(trainingQueryFile!=null){
            TreeMap<Integer,String> queries = processQueryFile(trainingQueryFile);
            relMap = readQrelsFile();//get the relevance map
            Map<String,Map<Integer,Double>> featureVector = getTrainFeatureVector(queries,relMap);
            writeFeatureVectors(trainingFeatureVectorsFile,featureVector,queries,relMap);
            trainModel();//train model
            RetrievalModelBM25 model = new RetrievalModelBM25(k1,b,k3);
            Map<String,List<Integer>> qryDocs = QryEval.processQueryFile(queryFilePath,trecEvalOutputPath,"100",model);
            TreeMap<Integer,String> testQries = processQueryFile(queryFilePath);
            Map<String,Map<Integer,Double>> testVector = getTestFeatureVector(testQries,qryDocs);
            writeFeatureVectors(testingFeatureVectorsFile,testVector,queries,null);
            rerankData(trecEvalOutputPath);
        }
    }

    private Map<Integer,Map<String,Integer>> readQrelsFile() throws IOException {
        Scanner scanner = new Scanner(trainingQrelsFile);
        Map<Integer,Map<String,Integer>> relMap = new HashMap<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(trainingQrelsFile));

            while ((qLine = input.readLine()) != null) {
                String[] info = qLine.split(" +");
                int qryID = Integer.parseInt(info[0]);
                String externalID = info[2];
                int rel = Integer.parseInt(info[3]);
                //relevance map,key is the query ID, value is the map with externalID as key and relevance value as value
                if(!relMap.containsKey(qryID)){
                    relMap.put(qryID,new HashMap<>());
                }
                relMap.get(qryID).put(externalID,rel);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return relMap;
    }
    private TreeMap<Integer,String> processQueryFile(String queryFilePath)
            throws IOException {
        TreeMap<Integer,String> queries = new TreeMap<>();
        BufferedReader input = null;
        try {
            String line = null;
            input = new BufferedReader(new FileReader(queryFilePath));
            while ((line = input.readLine()) != null) {
                int d = line.indexOf(':');
                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }
                int qid = Integer.parseInt(line.substring(0, d));
                String query = line.substring(d + 1);
                queries.put(qid,query);
                System.out.println("Query " + line);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
        return queries;
    }


    private List<String> getTokens(String query) throws IOException {
//        for(Map.Entry<Integer,String> entry:queries.entrySet()){
        List<String> queryStems = new ArrayList<>();
        String[] terms = query.split(" +");
        for(String term:terms){
            queryStems.addAll(Arrays.asList(QryParser.tokenizeString(term)));
        }
        return queryStems;
    }
    private void updateMaxAndMin(int featureIdx,Double score,int queryID){
        if(maxMap.get(queryID)==null){
            maxMap.put(queryID, new HashMap<Integer, Double>());
            maxMap.get(queryID).put(featureIdx,-Double.MAX_VALUE);
        }else {
            if (maxMap.get(queryID).get(featureIdx) == null || maxMap.get(queryID).get(featureIdx) < score) {
                maxMap.get(queryID).put(featureIdx, score);
            }
        }
        if(minMap.get(queryID)==null) {
            minMap.put(queryID, new HashMap<Integer, Double>());
            minMap.get(queryID).put(featureIdx,Double.MAX_VALUE);
        }else{
            if (minMap.get(queryID).get(featureIdx) == null || minMap.get(queryID).get(featureIdx) > score) {
                minMap.get(queryID).put(featureIdx, score);
            }
        }
    }

    /**
     * This method is to get the feature vector map for documents in relevance judgements and in top 100 rankings
     * @return
     * @throws Exception
     */
    private Map<String,Map<Integer,Double>> getTrainFeatureVector(TreeMap<Integer,String> queries,Map<Integer,Map<String,Integer>> relMap) throws Exception {
        Map<String,Map<Integer,Double>> featureVector = new HashMap<>();
        qryDocs = new HashMap<>();
        //iterate through every document
        for(Map.Entry<Integer,String> qryEntry:queries.entrySet()){
            int qryID = qryEntry.getKey();
            qryDocs.put(qryID,new ArrayList<>());
            List<String> queryStems = getTokens(qryEntry.getValue());
            Map<String,Integer> map = relMap.get(qryID);
            for(Map.Entry<String,Integer> docEntry: map.entrySet()){
                try {
                    int docID = Idx.getInternalDocid(docEntry.getKey());

                qryDocs.get(qryID).add(docID);
//            for(int docID:docs){
                Map<Integer,Double> docFeature = new HashMap<>();
                calculateFeatures(docFeature,docID,qryID,queryStems);
              //  featureVector.put(docEntry.getKey(),docFeature);
                featureVector.put(Idx.getExternalDocid(docID),docFeature);
                }catch (Exception e){
                    continue;
                }
            }

        }
        return featureVector;
    }

    private Map<String,Map<Integer,Double>> getTestFeatureVector(TreeMap<Integer,String> queries,Map<String,List<Integer>> documents) throws Exception {
        Map<String,Map<Integer,Double>> featureVector = new HashMap<>();
        //iterate through every document
        for(Map.Entry<Integer,String> qryEntry:queries.entrySet()){
            int qryID = qryEntry.getKey();
            List<Integer> docs = documents.get(qryID+"");
            List<String> queryStems = getTokens(qryEntry.getValue());
            for(int docID:docs){
                Map<Integer,Double> docFeature = new HashMap<>();
                calculateFeatures(docFeature,docID,qryID,queryStems);
                //  featureVector.put(docEntry.getKey(),docFeature);
                featureVector.put(Idx.getExternalDocid(docID),docFeature);
            }

        }
        return featureVector;
    }

    private void calculateFeatures(Map<Integer,Double> docFeature,int docID,int queryid,List<String> queryStems) throws IOException {
        if(!featureDisable.contains(1)){
            double spamScore = Double.parseDouble (Idx.getAttribute ("spamScore", docID));
            docFeature.put(1,spamScore);
                updateMaxAndMin(1, spamScore, queryid);
        }
        else{
            docFeature.put(1,-1.0);
        }
        String rawUrl = Idx.getAttribute ("rawUrl", docID);
        if(!featureDisable.contains(2)){
            double numSlash = 0.0;
            for(char c:rawUrl.toCharArray()){
                if(c=='/')
                    numSlash++;
            }
            docFeature.put(2,numSlash-2);//minus the number of prefix in http://
                updateMaxAndMin(2,numSlash-2,queryid);
        }
        else{
            docFeature.put(2,-1.0);
        }
        if(!featureDisable.contains(3)){
            double wikiScore = rawUrl.toLowerCase().contains("wikipedia.org")?1:0;
            docFeature.put(3,wikiScore);
            updateMaxAndMin(3,wikiScore,queryid);
        }
        else{
            docFeature.put(3,-1.0);
        }
        if(!featureDisable.contains(4)) {
            double prScore = Float.parseFloat(Idx.getAttribute("PageRank", docID));
            docFeature.put(4, prScore);
            updateMaxAndMin(4,prScore,queryid);
        }
        else{
            docFeature.put(4,-1.0);
        }
        if(!featureDisable.contains(5)){
            double bm25 = getFeatureBM25(queryStems,docID,"body");
            docFeature.put(5,bm25);
            if(bm25!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(5,bm25,queryid);
        }
        else{
            docFeature.put(5,-1.0);
        }
        if(!featureDisable.contains(6)){
            double indri = getFeatureIndri(queryStems,docID,"body");
            docFeature.put(6,indri);
            if(indri!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(6,indri,queryid);
        }
        else{
            docFeature.put(6,-1.0);
        }
        if(!featureDisable.contains(7)){
            double overLap = getOverlapScore(queryStems,docID,"body");
            docFeature.put(7,overLap);
            if(overLap!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(7,overLap,queryid);
        }
        else{
            docFeature.put(7,-1.0);
        }
        if(!featureDisable.contains(8)){
            double bm25 = getFeatureBM25(queryStems,docID,"title");
            docFeature.put(8,bm25);
            if(bm25!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(8,bm25,queryid);
        }
        else{
            docFeature.put(8,-1.0);
        }
        if(!featureDisable.contains(9)){
            double indri = getFeatureIndri(queryStems,docID,"title");
            docFeature.put(9,indri);
            if(indri!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(9,indri,queryid);
        }
        else{
            docFeature.put(9,-1.0);
        }
        if(!featureDisable.contains(10)){
            double overLap = getOverlapScore(queryStems,docID,"title");
            docFeature.put(10,overLap);
            if(overLap!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(10,overLap,queryid);
        }
        else{
            docFeature.put(10,-1.0);
        }
        if(!featureDisable.contains(11)){
            double bm25 = getFeatureBM25(queryStems,docID,"url");
            docFeature.put(11,bm25);
            if(bm25!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(11,bm25,queryid);
        }
        else{
            docFeature.put(11,-1.0);
        }
        if(!featureDisable.contains(12)){
            double indri = getFeatureIndri(queryStems,docID,"url");
            docFeature.put(12,indri);
            if(indri!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(12,indri,queryid);
        }
        else{
            docFeature.put(12,-1.0);
        }
        if(!featureDisable.contains(13)){
            double overLap = getOverlapScore(queryStems,docID,"url");
            docFeature.put(13,overLap);
            if(overLap!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(13,overLap,queryid);
        }
        else{
            docFeature.put(13,-1.0);
        }
        if(!featureDisable.contains(14)){
            double bm25 = getFeatureBM25(queryStems,docID,"inlink");
            docFeature.put(14,bm25);
            if(bm25!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(14,bm25,queryid);
        }
        else{
            docFeature.put(14,-1.0);
        }
        if(!featureDisable.contains(15)){
            double indri = getFeatureIndri(queryStems,docID,"inlink");
            docFeature.put(15,indri);
            if(indri!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(15,indri,queryid);
        }
        else{
            docFeature.put(15,-1.0);
        }
        if(!featureDisable.contains(16)){
            double overLap = getOverlapScore(queryStems,docID,"inlink");
            docFeature.put(16,overLap);
            if(overLap!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(16,overLap,queryid);
        }
        else{
            docFeature.put(16,-1.0);
        }
        if(!featureDisable.contains(17)){
            double overLap = getMatchScore(queryStems,docID,"body");
            docFeature.put(17,overLap);
            if(overLap!=Double.NEGATIVE_INFINITY)
                updateMaxAndMin(17,overLap,queryid);
        }
        else{
            docFeature.put(17,-1.0);
        }
        if(!featureDisable.contains(18)){
            double queryLen = queryStems.size();
            docFeature.put(18,queryLen);
            updateMaxAndMin(18,queryLen,queryid);
        }
        else{
            docFeature.put(18,-1.0);
        }

    }

    private double getFeatureBM25(List<String> queryStems,int docid,String field) throws IOException {
        double score = 0.0;
        TermVector tv = new TermVector(docid,field);
        for(String stem:queryStems){
//            System.out.print(stem+" "+stem.length()+" ");//for testing
            if (tv.stemsLength() == 0||tv.positionsLength()==0){
                return Double.NEGATIVE_INFINITY;

            }
            int stemIdx = tv.indexOfStem(stem);
            double idf = 0.0;
            double tfWeight = 0.0;
          //  double qtf = 0;
            if(stemIdx!=-1){
                double N = Idx.getNumDocs();
                double df = tv.stemDf(stemIdx);
                idf = Math.max(0,Math.log((N-df+0.5)/(df+0.5)));
                double tf = tv.stemFreq(stemIdx);
                double docLen = Idx.getFieldLength (field,docid);
                double avgLen = Idx.getSumOfFieldLengths(field)/
                        (double) Idx.getDocCount (field);
                tfWeight = tf/(tf+k1*(1-b+b*docLen/avgLen));
               // qtf = (k3 + 1) * 1 / (k3 + 1);
            }
            score += idf*tfWeight*1;
        }
//        System.out.println();
        return score;
    }
    private double getFeatureIndri(List<String> queryStems,int docid,String field) throws IOException {
        double score = 1.0;
        double docLen = Idx.getFieldLength(field,docid);
        double totalLen = Idx.getSumOfFieldLengths(field);
        TermVector tv = new TermVector(docid,field);
        if(tv.stemsLength()==0||tv.positionsLength()==0) return Double.NEGATIVE_INFINITY;
        //changed 0.0 to 1.0

        boolean match = false;//to check whether at least one query term matches
        for(String stem:queryStems){
            int idx = tv.indexOfStem(stem);
            double tf = 0.0;
            double probColl = 0.0;
            if(idx!= -1) {
                match = true;
                double ctf = tv.totalStemFreq(idx);
                probColl = ctf / totalLen;
                tf = tv.stemFreq(idx);
            }else{
                double ctf = Idx.getTotalTermFreq(field, stem);
                probColl = ctf/totalLen;
                tf = 0;
            }
            score *=(1-lambda)*(tf+mu*probColl)/(docLen+mu)+lambda*probColl;
        }
        if(!match) return 0.0;
        score = Math.pow(score, 1.0 /queryStems.size());
        return score;
    }
    private double getMatchScore(List<String> queryStems,int docid,String field) throws IOException{
        TermVector tv = new TermVector(docid,field);
        int numMatch = 0;
        //   if(tv.stemsLength()==0) return 0.0;//changed 0.0 to 1.0
        if (tv.positionsLength() == 0 || tv.stemsLength() == 0){
            return Double.NEGATIVE_INFINITY;
        }
        for(String stem:queryStems){
            int idx = tv.indexOfStem(stem);
            if(idx!=-1) numMatch++;
        }
        return numMatch*1.0;
    }
    private double getOverlapScore(List<String> queryStems,int docid,String field) throws IOException{
        TermVector tv = new TermVector(docid,field);
        int numMatch = 0;
     //   if(tv.stemsLength()==0) return 0.0;//changed 0.0 to 1.0
        if (tv.positionsLength() == 0 || tv.stemsLength() == 0){
            return Double.NEGATIVE_INFINITY;
        }
        for(String stem:queryStems){
            int idx = tv.indexOfStem(stem);
            if(idx!=-1) numMatch++;
        }
        return numMatch/(queryStems.size()*1.0);
    }
    public void writeFeatureVectors(String outputPath,Map<String,Map<Integer,Double>> featureVector,TreeMap<Integer,String> queries,Map<Integer,Map<String,Integer>> relMap) throws Exception {
        PrintWriter writer = new PrintWriter(outputPath);
        for(Map.Entry<Integer,String> query:queries.entrySet()){
            int qryid = query.getKey();
            //feature vector key is the external id document,value is the feature and corresponding feature value
            //feature vector is calculated by iterating through relevance map and get every document feature
            //relevance map key is the query id, value is the external document id and relevance score
            for(Map.Entry<String,Map<Integer,Double>> mapEntry:featureVector.entrySet()){
                String externalID = mapEntry.getKey();//this document id can be document not consisted in the relevant query id
                int relevanceScore = 0;
                if(relMap!=null) {
                    if(relMap.get(qryid)==null||relMap.get(qryid).get(externalID)==null) continue;
                    relevanceScore = relMap.get(qryid).get(externalID);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(relevanceScore).append(" qid:").append(qryid).append(" ");
                for(int i = 1;i<=mapEntry.getValue().size();i++){
                    if(!featureDisable.contains(i)||mapEntry.getValue().get(i) != -1) {
                            double val = mapEntry.getValue().get(i);
                            if (val == Double.NEGATIVE_INFINITY) {
                                sb.append(i).append(":").append(0.0).append(" ");
                            } else {
                                double diff = maxMap.get(qryid).get(i) - minMap.get(qryid).get(i);
                                if (diff > 0) {
                                    sb.append(i).append(":").append((val - minMap.get(qryid).get(i)) / diff).append(" ");
                                } else {
                                    sb.append(i).append(":").append(0.0).append(" ");
                                }
                            }
                        } else {
                            sb.append(i).append(":").append(0.0).append(" ");
                        }
                }
                sb.append("#").append(" ").append(externalID);
                writer.println(sb.toString());

            }
        }

        writer.close();

    }

    public void trainModel() throws Exception {
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankLearnPath, "-c", String.valueOf(svmRankParamC), trainingFeatureVectorsFile,
                        svmRankModelFile });
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }

    }
    public void rerankData(String trecEvalOutputPath) throws Exception{
        //call svm rank to test the data
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { svmRankClassifyPath, testingFeatureVectorsFile,
                        svmRankModelFile,testingDocumentScores});
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
        //qrelsFeatures output file
        BufferedReader scoreReader = new BufferedReader(new FileReader(testingDocumentScores));
//        BufferedReader vectorReader = new BufferedReader(new FileReader(trecEvalOutputPath));
        String score = null;
        String feature = null;
        PrintWriter writer = new PrintWriter(trecEvalOutputPath);
        for(Map.Entry<Integer,List<Integer>> entry:qryDocs.entrySet()) {
            ScoreList r = new ScoreList();
            int qryid = entry.getKey();
            int count = 0;
            while (count<entry.getValue().size()&&(score = scoreReader.readLine()) != null) {
                double svmScore = Double.parseDouble(score);
                //one query one scorelist
                //how to get one query
                int docid = entry.getValue().get(count);
                r.add(docid,svmScore);
                count++;
            }
            QryEval.printResults(qryid+"","100",r,writer);
        }
        writer.close();

    }


    @Override
    public String defaultQrySopName() {
        return null;
    }
}
