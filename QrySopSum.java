import java.io.IOException;

/**
 * Created by apple on 14/2/19.
 */
public class QrySopSum extends QrySop {

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        return 0;
    }

    private double getScoreBM25(RetrievalModel r) throws IOException{
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double score = 0.0;
            int doc_id = this.docIteratorGetMatch();//one of the matching document of current query according to the matching standard
            for (Qry q : this.args) {
                if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id) {//current query argument has match to th same document
                    score +=  ((QrySop) q).getScore(r);
                }
            }
            return score;
        }
    }
}
