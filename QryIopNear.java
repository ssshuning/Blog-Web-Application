import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by apple on 26/1/19.
 */
public class QryIopNear extends QryIop {
    private int distance;

    public QryIopNear(int distance) {
        this.distance = distance;
    }


    /**
     * Evaluate the query operator; the result is an internal inverted
     * list that may be accessed via the internal iterators.
     *
     * @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate() throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        /* near implementation:
           Come to the document that meeting all queries' requirements
           then use locIterator to iterate the locations
           make sure the right location is greater than the left location
           If the distance is larger than the parameter, advance the left most location iterator
           If matched, advance all location iterators
        */

        while (this.docIteratorHasMatchAll(null)) {
            int doc_0 = args.get(0).docIteratorGetMatch();//get a document that all query requirements are met
            QryIop qry_0 = (QryIop) args.get(0);
            List<Integer> positions = new ArrayList<>();
            QryIop qryIop;
            boolean hasLoc = false;
            while (qry_0.locIteratorHasMatch()) {
                int prev_loc = qry_0.locIteratorGetMatch();
                boolean match = true;
                for (int i = 1; i < args.size(); i++) {
                    qryIop = (QryIop) args.get(i);
                    qryIop.locIteratorAdvancePast(prev_loc);//always pass the last location
                    if(!qryIop.locIteratorHasMatch()){
                        hasLoc = true;
                        break;
                    }
                    int curr_loc = qryIop.locIteratorGetMatch();
                    if (curr_loc > prev_loc + distance) {//judging the distance between two locations
                        match = false;
                    }
                    prev_loc = curr_loc;

                }
                if (hasLoc) break;//we need to break before entering the if block because match is true after breaking the for loop
                if (!match) {
                    qry_0.locIteratorAdvance();//if not matched, advance the left most pointer
                } else {
                    positions.add(prev_loc);//prev_loc has been updated to the rightmost location
                    for(Qry q:args){
                        ((QryIop)q).locIteratorAdvance();
                    }
                }
            }
            if (positions.size() != 0) {
                this.invertedList.appendPosting(doc_0, positions);
            }
            qry_0.docIteratorAdvancePast(doc_0);

        }
    }

}
