
import java.io.IOException;

/**
 * Created by apple on 27/1/19.
 */
public class QrySopAnd extends QrySop {
    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if(r instanceof RetrievalModelIntri){
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll(r);
    }

    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIntri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double score = 1.0;
        for(Qry qry:this.args){
            score *= ((QrySop)qry).getDefaultScore(r,docid);
        }
        return Math.pow(score,1.0/this.args.size());
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 1.0;
        int docid = this.docIteratorGetMatch();//this is the document with the smallest document id to have at least one term
        if(!this.docIteratorHasMatch(r)){
            return 0.0;
        }else{
            for(Qry qry:this.args){
                if(qry.docIteratorHasMatch(r)&&qry.docIteratorGetMatch()==docid){
                    score *= ((QrySop)qry).getScore(r);
                }else{//what if query has match but getMatch() is not equal to docid?
                    score *= ((QrySop)qry).getDefaultScore(r,docid);
                }
            }
            return Math.pow(score,1.0/this.args.size());

        }
    }

    /**
     * getScore for the UnrankedBoolean retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {//get the minimum score
            double score = Double.MAX_VALUE;
       //     int doc_id = this.docIteratorGetMatch();//one of the matching document of current query according to the matching standard
            for (Qry q : this.args) {
                if (q.docIteratorHasMatch(r)) {
                    score = Math.min(score, ((QrySop) q).getScore(r));
                }
            }
            return score;
        }
    }

}
