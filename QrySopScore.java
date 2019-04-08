/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        }else if(r instanceof RetrievalModelBM25){
            return this.getScoreBM25(r);
        } else if(r instanceof RetrievalModelIntri){
            return this.getScoreIntri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }
    private double getScoreIntri(RetrievalModel r) throws IOException{
        if(!this.docIteratorHasMatchCache()){
            return 0.0;
        }else {
            Qry q = this.args.get(0);
          //  double probColl = Idx.getTotalTermFreq(((QryIop)q).getField(),((QryIopTerm)q).getTerm());
            double ctf = ((QryIop)q).getCtf();
            double probColl = ctf/Idx.getSumOfFieldLengths(((QryIop)q).getField());
            double lambda = ((RetrievalModelIntri) r).getLambda();
            double tf = ((QryIop)q).docIteratorGetMatchPosting().tf;
            double mu = ((RetrievalModelIntri) r).getMu();
            double docLen = Idx.getFieldLength(((QryIop)q).getField(),q.docIteratorGetMatch());
            double result = (1-lambda)*(tf+mu*probColl)/(docLen+mu)+lambda*probColl;
            return result;
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if(r instanceof RetrievalModelIntri) {
            Qry q = this.args.get(0);
         //   double probColl = Idx.getTotalTermFreq(((QryIop)q).getField(),((QryIopTerm)q).getTerm());
            double ctf = ((QryIop)q).getCtf();
            double probColl = ctf/Idx.getSumOfFieldLengths(((QryIop)q).getField());
            if(ctf==0) probColl = 0.5;
            double lambda = ((RetrievalModelIntri) r).getLambda();
            double mu = ((RetrievalModelIntri) r).getMu();
            double docLen = Idx.getFieldLength(((QryIop)q).getField(), (int)docid);
            double result = (1-lambda)*mu*probColl/(docLen+mu)+lambda*probColl;
            return result;
        }
        return 0.0;
    }

    /**
     *  getScore for the Unranked retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {//shuning:only has one single argument
            Qry q = this.args.get(0);//the only argument
            if (q.docIteratorHasMatch(r)) {
                return ((QryIop)q).docIteratorGetMatchPosting().tf;
            }
            return 0.0;
        }
    }



    /**
     * Method for calculating score of BM25
     * @param r the retrieval model given
     * @return return the score value
     * @throws IOException throw exception
     */
    public double getScoreBM25(RetrievalModel r) throws IOException{
        if(!this.docIteratorHasMatchCache()){
            return 0.0;
        } else{
            Qry q= this.args.get(0);
            double N = Idx.getNumDocs();
            double df = ((QryIop)q).getDf();
            double idf = Math.log((N-df+0.5)/(df+0.5));
            double k_1 = ((RetrievalModelBM25)r).getK_1();
            double b = ((RetrievalModelBM25)r).getB();
            double k_3 = ((RetrievalModelBM25)r).getK_3();
            double tf = ((QryIop)q).docIteratorGetMatchPosting().tf;
            double docLen = Idx.getFieldLength (((QryIop)q).getField(), this.docIteratorGetMatch());
            double avgLen = Idx.getSumOfFieldLengths(((QryIop)q).getField()) /
                    (double) Idx.getDocCount (((QryIop)q).getField());
            double tfWeight = tf/(tf+k_1*(1-b+b*docLen/avgLen));

            double result = idf*tfWeight*1;
            return result;
        }
    }

    /**
     *  Initialize the query operator (and its arguments), including any
     *  internal iterators.  If the query operator is of type QryIop, it
     *  is fully evaluated, and the results are stored in an internal
     *  inverted list that may be accessed via the internal iterator.
     *  @param r A retrieval model that guides initialization
     *  @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

}
