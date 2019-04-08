import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by apple on 15/2/19.
 */
public class QryIopWindow extends QryIop {
    private double operatorDistance;

    public QryIopWindow(double operatorDistance) {
        this.operatorDistance = operatorDistance;
    }


    @Override
    protected void evaluate() throws IOException {
        /**
         * Evaluate the query operator; the result is an internal inverted
         * list that may be accessed via the internal iterators.
         *
         * @throws IOException Error accessing the Lucene index.
         */
        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        /* window implementation:
           Come to the document that meeting all queries' requirements
           then use locIterator to iterate the locations
           find the maximum and minimum location and compare the distance
           If the distance is larger than the parameter, advance the minimum location iterator
           If matched, advance all location iterators
        */

        while (this.docIteratorHasMatchAll(null)) {
            int doc_0 = this.args.get(0).docIteratorGetMatch();//get a document that all query requirements are met
            QryIop qry_0 = (QryIop) args.get(0);
            List<Integer> positions = new ArrayList<>();
            boolean hasLoc = true;
            while (qry_0.locIteratorHasMatch()) {
                int prev_loc = qry_0.locIteratorGetMatch();
                int max_loc = prev_loc;
                int min_loc = prev_loc;
                QryIop min_qry = qry_0;
                for (int i = 1; i < args.size(); i++) {
                    QryIop qryIop = (QryIop) args.get(i);

                    if (!qryIop.locIteratorHasMatch()) {
                        hasLoc = false;

                        break;
                    }
                    int loc = qryIop.locIteratorGetMatch();
                    if (loc < min_loc) {
                        min_loc = loc;
                        min_qry = qryIop;
                    }
                    if (loc > max_loc) {
                        max_loc = loc;
                    }
                }


                if (!hasLoc)
                    break;//we need to break here because there is no more location
                if (max_loc - min_loc+1> operatorDistance) {
                    (min_qry).locIteratorAdvance();//do i need to add 1 here?
                 //   ((QryIop)this.args.get(0)).locIteratorAdvance();
                } else {
                    positions.add(max_loc);
                    for (Qry q : args) {
                        ((QryIop) q).locIteratorAdvance();
                    }
                }

            }

            if (positions.size() != 0) {
                this.invertedList.appendPosting(doc_0, positions);
            }
            for (Qry q_i : this.args) {
                q_i.docIteratorAdvancePast(doc_0);
            }

        }

    }
}
