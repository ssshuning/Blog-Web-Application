/**
 * Created by apple on 14/2/19.
 */
public class RetrievalModelIntri extends RetrievalModel {
    private double mu;
    private double lambda;

    public double getMu() {
        return mu;
    }

    public double getLambda() {
        return lambda;
    }

    public RetrievalModelIntri(double mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }

    @Override
    public String defaultQrySopName() {
        return "#and";
    }
}
