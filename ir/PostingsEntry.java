package ir;

import java.util.ArrayList;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }

    /**
     * Used to sort the ArrayList<PostingsEntry> in a descending order according to their scores.
     */
    public static class scoreComparator implements Comparator<PostingsEntry> {
        @Override
        public int compare (PostingsEntry pl1, PostingsEntry pl2) {
            return Double.compare(pl2.score, pl1.score);
        }
    }

    //
    // YOUR CODE HERE
    //

    public ArrayList<Integer> offset = new ArrayList<Integer>();

    /* contructor for unranked retrieval */
    public PostingsEntry (int docID) {
        this.docID = docID;
    }

    /* constructor for ranked retrieval */
    public PostingsEntry (int docID, double score) {
        this.docID = docID;
        this.score = score;
    }

    /* Construct from String */
    public PostingsEntry (String rep) {
        String[] docIDOffset = rep.split(":");
        this.docID = Integer.parseInt(docIDOffset[0]);
        String[] offsetVals = docIDOffset[1].split(",");
        for (String offsetVal : offsetVals) {
            offset.add(Integer.parseInt(offsetVal));
        }
    }

    /* Copy constructor */
    public PostingsEntry (PostingsEntry postingsEntry) {
        this.docID = postingsEntry.docID;
        this.score = postingsEntry.score;
        this.offset = new ArrayList<Integer>(postingsEntry.offset);
    }
    
    /* add a single offset value into the current one (order maintenance) */
    public void addOffset(int offset_val) {
        if (this.offset.isEmpty()) {
            this.offset.add(offset_val);
            return;
        }

        int index = Collections.binarySearch(offset, offset_val);
        if (index >= 0) {
            this.offset.add(index, offset_val);
        }
        else {
            this.offset.add(-(index + 1), offset_val);
        }
    }

    /* merge another ordered offset list into the current one (order maintenance) */
    public void addOffset(ArrayList<Integer> offset) {
        if (offset == null) {
            return;
        }

        int i1 = 0, i2 = 0;
        while (i1 < this.offset.size() && i2 < offset.size()) {
            if (this.offset.get(i1) < offset.get(i2)) {
                i1++;
            }
            else {
                this.offset.add(i1, offset.get(i2));
                i1++;
                i2++;
            }
        }
        while (i2 < offset.size()) {
            this.offset.add(offset.get(i2));
            i2++;
        }
    }

    /* For debugging */
    public void print() {
        System.out.print(this.docID + ": [");
        for (int i = 0; i < this.offset.size(); i++) {
            if (i == this.offset.size() - 1) {
                System.out.println(this.offset.get(i) + "]");
            } else {
                System.out.print(this.offset.get(i) + ",");
            }
        }
    }

    public String toString() {
        String rep = new String();
        rep = rep.concat(this.docID + ":");
        for (int j = 0; j < this.offset.size(); j++) {
            if (j == this.offset.size() - 1) {
                rep = rep.concat(this.offset.get(j).toString());
                break;
            }
            rep = rep.concat(this.offset.get(j) + ",");
        }
        rep = rep.concat(".");
        return rep;
    }
}

