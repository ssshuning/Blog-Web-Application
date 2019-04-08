import java.io.IOException;
import java.util.List;

/**
 * Created by apple on 15/2/19.
 */
public class QrySopWSum extends QrySop {

    private List<Double> weights;
    private double totalWeight;

    public QrySopWSum() {
        totalWeight = 0.0;
    }

    public QrySopWSum(List<Double> weights) {
        this.weights = weights;
        totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }

    }

    public void setWeights(List<Double> weights) {
        this.weights = weights;
        totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIntri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 0.0;
        int docid = this.docIteratorGetMatch();//this is the document with the smallest document id to have at least one term
        if(!this.docIteratorHasMatch(r)){
            return 0.0;
        }else{
            for(int i = 0;i<args.size();i++){
                QrySop q = (QrySop) args.get(i);
                double tmpScore = 0.0;
                if(q.docIteratorHasMatch(r)&&q.docIteratorGetMatch()==docid){
                    tmpScore = q.getScore(r)*weights.get(i)/totalWeight;
                    score += tmpScore;
                }else{//what if query has match but getMatch() is not equal to docid?
                    tmpScore = q.getDefaultScore(r,docid)*weights.get(i)/totalWeight;
                    score += tmpScore;
                }
            }
            return score;

        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double score = 0.0;
        for(int i = 0;i<args.size();i++){
            QrySop q = (QrySop) args.get(i);
            double tmpScore = q.getDefaultScore(r,docid)*weights.get(i)/totalWeight;
            score += tmpScore;
        }
        return score;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }
}
