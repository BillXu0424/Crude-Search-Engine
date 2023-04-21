package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.print.Doc;

import java.util.Collections;
import java.util.Comparator;

public class PostingsList {
    
    /** The postings list */
    private ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();
    /* Hashmap used to reduce searching complexity from O(n) to O(1). */
    private HashMap<Integer, PostingsEntry> mapSearch = new HashMap<Integer, PostingsEntry>();

    /** Number of postings in this list. */
    public int size() {
    return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
    return list.get( i );
    }

    // 
    //  YOUR CODE HERE
    //
    public PostingsList() {}

    public PostingsList(ArrayList<PostingsEntry> list) {
        this.list = new ArrayList<>(list);
        for (PostingsEntry pe : this.list) {
            this.mapSearch.put(pe.docID, pe);
        }
    }

    public PostingsList(PostingsList postingsList) {
        this.list = new ArrayList<PostingsEntry>();
        for (int i = 0; i < postingsList.size(); i++) {
            PostingsEntry postingsEntry = new PostingsEntry(postingsList.get(i));
            this.mapSearch.put(postingsEntry.docID, postingsEntry);
            this.list.add(new PostingsEntry(postingsEntry));
        }
    }

    public PostingsList(String rep) {
        String[] postingsEntryReps = rep.split("\\.");
        for (String postingsEntryRep : postingsEntryReps) {
            PostingsEntry newPostingsEntry = new PostingsEntry(postingsEntryRep);
            list.add(newPostingsEntry);
            mapSearch.put(newPostingsEntry.docID, newPostingsEntry);
        }
    }
    
    /* Be careful that the postingEntry here is initially without offset added */
    /* This method adds a single offset int value */
    public void addPostingsEntry (PostingsEntry postingsEntry, int offset) {
        int docID = postingsEntry.docID;

        if (list.isEmpty()) {
            /* offset == -1: offset is a don't-care in INTERSECTION_QUERY */
            if (offset != -1) {
                postingsEntry.addOffset(offset);
            }
            this.list.add(postingsEntry);
            mapSearch.put(docID, postingsEntry);
        }
        else {
            PostingsEntry entryWithSameDocID = findEntryWithSameDocID(postingsEntry);
            if (entryWithSameDocID == null) {
                if (offset != -1) {
                    postingsEntry.addOffset(offset);
                }
                //sortedAdd(postingsEntry);
                this.list.add(postingsEntry);
                mapSearch.put(docID, postingsEntry);
            }
            else {
                if (offset != -1) {
                    entryWithSameDocID.addOffset(offset);
                }
            }
        }
    }

    /* Be careful that the postingEntry here can have initial offset list */
    /* This overloaded method adds a whole offset (ArrayList<Integer>) */
    public void addPostingsEntry (PostingsEntry postingsEntry, ArrayList<Integer> offset) {
        int docID = postingsEntry.docID;

        if (list.isEmpty()) {
            postingsEntry.addOffset(offset);
            this.list.add(postingsEntry);
            mapSearch.put(docID, postingsEntry);
        }
        else {
            PostingsEntry entryWithSameDocID = findEntryWithSameDocID(postingsEntry);
            if (entryWithSameDocID == null) {
                postingsEntry.addOffset(offset);
                this.list.add(postingsEntry);
                mapSearch.put(docID, postingsEntry);
            }
            else {
                entryWithSameDocID.addOffset(offset);
            }
        }
    }

    /**
     * This function is used for merging wildcard queries in intersection, phrase search.
     * @param pl2
     */
    public void mergePl(PostingsList pl2) {
        if (pl2 == null) return;

        int i1 = 0, i2 = 0;

        while (i1 < this.size() && i2 < pl2.size()) {
            if (this.get(i1).docID < pl2.get(i2).docID) {
                i1++;
            }
            else if (this.get(i1).docID > pl2.get(i2).docID) {
                PostingsEntry entry = new PostingsEntry(pl2.get(i2));
                this.list.add(i1, entry);
                this.mapSearch.put(entry.docID, entry);
                i1++;
                i2++;
            }
            else{
                int j1 = 0, j2 = 0;
                while (j1 < this.get(i1).offset.size() && j2 < pl2.get(i2).offset.size()) {
                    if (this.get(i1).offset.get(j1) < pl2.get(i2).offset.get(j2)) {
                        j1++;
                    }
                    else if (this.get(i1).offset.get(j1) > pl2.get(i2).offset.get(j2)) {
                        this.get(i1).offset.add(j1, pl2.get(i2).offset.get(j2));
                        j1++;
                        j2++;
                    }
                    else {
                        this.get(i1).offset.add(j1, pl2.get(i2).offset.get(j2));
                        j1++;
                        j2++;
                    }
                }
                if (j2 < pl2.get(i2).offset.size()) {
                    for (int k = j2; k < pl2.get(i2).offset.size(); k++) {
                        this.get(i1).offset.add(pl2.get(i2).offset.get(k));
                    }
                }
                i1++;
                i2++;
            }
        }
        if (i2 < pl2.size()) {
            for (int i = i2; i < pl2.size(); i++) {
                PostingsEntry entry = new PostingsEntry(pl2.get(i));
                this.list.add(entry);
                this.mapSearch.put(entry.docID, entry);
            }
        }
    }

    private PostingsEntry findEntry (int docID) {
        return mapSearch.get(docID);
    }

    private PostingsEntry findEntryWithSameDocID (PostingsEntry postingsEntry) {
        return findEntry(postingsEntry.docID);
    }

    public void appendEntry(PostingsEntry postingsEntry) {
        this.list.add(postingsEntry);
        this.mapSearch.put(postingsEntry.docID, postingsEntry);
    }

    public static void sortByIncreasingFrequency(ArrayList<PostingsList> qtPostings) {
        FrequencyComparator cmp = new FrequencyComparator();
        Collections.sort(qtPostings, cmp);
    }

    public static class FrequencyComparator implements Comparator<PostingsList> {
        @Override
        public int compare (PostingsList pl1, PostingsList pl2) {
            int size1 = 0, size2 = 0;
            if (pl1 != null) {
                size1 = pl1.size();
            }
            if (pl2 != null) {
                size2 = pl2.size();
            }
            return Integer.compare(size1, size2);
        }
    }

    public String toString() {
        String rep = new String();
        for (PostingsEntry postingsEntry : this.list) {
            rep = rep.concat(postingsEntry.toString());
        }
        return rep;
    }

    /* For debugging */
    public void print() {
        System.out.println("--------------------------------------------------");
        for (PostingsEntry postingsEntry : list) {
            postingsEntry.print();
        }
        System.out.println("--------------------------------------------------");
    }
}

