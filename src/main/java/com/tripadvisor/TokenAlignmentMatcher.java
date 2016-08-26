package com.tripadvisor;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Scores the match between two series of tokens by using a modified
 * <a href="https://en.wikipedia.org/wiki/Needleman%E2%80%93Wunsch_algorithm">Needleman-Wunsch alignment algorithm</a>
 * originally developed for aligning gene sequences. By using an
 * alignment algorithm, we hope to discern what mismatches are due
 * to missing information (e.g., request has "123 Needham" and location has "123 Needham Street")
 * which are ok, versus contradictory information ("123 Needham Street" vs "130 Needham Street")
 * which are not ok.
 * 
 * Specifically, we attempt to line up tokens in the request field with tokens in the candidate location field
 * and identify edits/mismatches, gaps/offsets, and matches. In the optimization we set the value of an edit
 * to be equal to a gap, such that we only allow gaps when it is better overall than an edit.
 * E.g, we could align "123 Needham Street" and "130 Needham Street" as
 * 123 -GAP- Needham Street
 * -GAP- 130 Needham Street
 * but that's not the simplest alignment, rather it should be
 * 123 Needham Street
 * 130 Needham Street
 * --- +++++++ ++++++
 * 
 * However, we allow a gap to be "free" relative to an edit if the token of one series exists somewhere in the other series.
 * E.g., "123 Needham" and "Needham 123" are a perfectly fine match.
 * 
 * After determining an alignment, the match is scored as follows:
 * each matching token gets weight = IDF (as computed by Lucene) * fraction of characters that match
 * each mismatching token gets weight = -IDF
 * each gap gets weight = -1.0, which should be significantly smaller than the -IDF of a useful token
 * The sum of these weights is reported. The caller can then normalize by sum(IDF) or by numTokens or use the raw score.
 * 
 * 
 * 
 * @author gamis
 * @since Oct 13, 2015
 *
 */
public class TokenAlignmentMatcher
{

    /**
     * Provides the tokens of a particular field in a listing request
     * and a candidate location we are trying to match it to
     */
    public interface FieldTokenMatchingPair
    {

        /**
         * Number of word tokens in the listing request.
         */
        int numRequestTokens();

        /**
         * Number of word tokens in the candidate location.
         */
        int numCandidateTokens();

        String requestTokenAt(int requestTokenIndex);

        double idfRequestTokenAt(int requestTokenIndex);

        String candidateTokenAt(int candidateTokenIndex);

        /**
         * Fraction of characters that match (in the edit-distance sense)
         * between the requested token at requestTokenIndex
         * and the candidate token at candidateTokenIndex.
         */
        double fracMatchingChars(int requestTokenIndex, int candidateTokenIndex);

        /**
         * Inverse document frequency of the token at requestTokenIndex.
         */

        /**
         * Find the distance of a request token if it exists elsewhere in the Candidate
         * 
         */
        int findTokenPosInCandidate(int reqRow);

        double idfCandidateTokenAt(int i);

        double sumRequestIDF();

        double sumCandidateIDF();

    }

    private static final int MATCH = 1;
    private static final int DELETE = 2;
    private static final int INSERT = 3;

    public double scoreMatch(final FieldTokenMatchingPair matchingPair)
    {
        final IntMatrix choices = _findAlignments(matchingPair);
        // printAlignment(matchingPair, choices);
        double score = _scoreAlignment(matchingPair, choices);

        return score;
    }

    private void _printAlignment(final FieldTokenMatchingPair matchingPair, final IntMatrix alignmentChoices)
    {
        int reqRow = alignmentChoices.numRows - 1;
        int candCol = alignmentChoices.numCols - 1;
        List<String> reqAlignment = new ArrayList<String>();
        List<String> candAlignment = new ArrayList<String>();

        while (reqRow > 0 || candCol > 0)
        {
            switch (alignmentChoices.c(reqRow, candCol))
            {
            case MATCH:
                reqAlignment.add(matchingPair.requestTokenAt(reqRow - 1));
                candAlignment.add(matchingPair.candidateTokenAt(candCol - 1));
                reqRow--;
                candCol--;
                break;
            case DELETE:
                reqAlignment.add(matchingPair.requestTokenAt(reqRow - 1));
                candAlignment.add("-");
                reqRow--;
                break;
            case INSERT:
                reqAlignment.add("-");
                candAlignment.add(matchingPair.candidateTokenAt(candCol - 1));
                candCol--;
                break;
            default:
                throw new IllegalStateException();
            }
        }
        for (int i = reqAlignment.size() - 1; i >= 0; i--)
        {
            String tok = reqAlignment.get(i);
            int width = Math.max(tok.length(), candAlignment.get(i).length()) + 1;
            System.out.print(StringUtils.rightPad(tok, width));
        }
        System.out.println();
        for (int i = candAlignment.size() - 1; i >= 0; i--)
        {
            String tok = candAlignment.get(i);
            int width = Math.max(tok.length(), reqAlignment.get(i).length()) + 1;
            System.out.print(StringUtils.rightPad(tok, width));
        }
        System.out.println();
    }

    /**
     * Walks the matrix of possible request/candidate token alignments to find
     * the best alignment (one with the fewest gaps and edits)
     * 
     * @param matchingPair
     * @return Matrix of choices, using MATCH, DELETE, and INSERT ints above
     */
    private IntMatrix _findAlignments(final FieldTokenMatchingPair matchingPair)
    {
        final DoubleMatrix matches = new DoubleMatrix(matchingPair.numRequestTokens() + 1, matchingPair.numCandidateTokens() + 1);
        final IntMatrix choices = new IntMatrix(matchingPair.numRequestTokens() + 1, matchingPair.numCandidateTokens() + 1);

        double deleteValue = -1;
        double mismatchValue = -1;
        double matchValue = 1;
        double insertValue = -1;

        for (int reqRow = 0; reqRow < matches.numRows; reqRow++)
        {

            for (int candCol = 0; candCol < matches.numCols; candCol++)
            {

                final double max; // Highest scoring option
                final int choice; // Choice that goes with highest scoring option
                if (reqRow == 0)
                {
                    max = -candCol;
                    choice = INSERT;
                }
                else if (candCol == 0)
                {
                    max = -reqRow;
                    choice = DELETE;
                }
                else
                {
                    insertValue = -matchingPair.idfCandidateTokenAt(candCol - 1);
                    mismatchValue = -matchingPair.idfRequestTokenAt(reqRow - 1);
                    deleteValue = -matchingPair.idfRequestTokenAt(reqRow - 1);
                    matchValue = matchingPair.idfRequestTokenAt(reqRow - 1);

                    double fracMatchingChars = matchingPair.fracMatchingChars(reqRow - 1, candCol - 1);
                    final double sim = fracMatchingChars > 0.65 ? matchValue : mismatchValue;
                    final double match = matches.c(reqRow - 1, candCol - 1) + sim; // Diagonal move, implies match or edit
                    final double delete = matches.c(reqRow - 1, candCol) + deleteValue; // Move down, implies gap in col (candidate)
                    final double insert = matches.c(reqRow, candCol - 1) + insertValue; // Move right, implies gap in row (request)
                    if (match > delete && match > insert)
                    {
                        max = match;
                        choice = MATCH;
                    }
                    else if (delete > insert)
                    {
                        max = delete;
                        choice = DELETE;
                    }
                    else
                    { // insert > delete
                        max = insert;
                        choice = INSERT;
                    }
                }
                matches.set(reqRow, candCol, max);
                choices.set(reqRow, candCol, choice);
            }
        }
        return choices;
    }

    /**
     * Determine the score for an alignment.
     * 
     * @param matchingPair
     * @param alignmentChoices using MATCH, DELETE, INSERT ints above
     * @return scores
     */
    private double _scoreAlignment(final FieldTokenMatchingPair matchingPair, final IntMatrix alignmentChoices)
    {
        double score = 0.0;
        int reqRow = alignmentChoices.numRows - 1;
        int candCol = alignmentChoices.numCols - 1;
        while (reqRow > 0 || candCol > 0)
        {
            switch (alignmentChoices.c(reqRow, candCol))
            {
            case MATCH:
                double fracMatched = matchingPair.fracMatchingChars(reqRow - 1, candCol - 1);
                double idf = matchingPair.idfRequestTokenAt(reqRow - 1);

                if (fracMatched > .65)
                {
                    score += fracMatched * idf;
                }
                else
                {
                    int pos = matchingPair.findTokenPosInCandidate(reqRow - 1);
                    if (pos != -1)
                    {
                        fracMatched = matchingPair.fracMatchingChars(reqRow - 1, pos);
                        score += fracMatched * idf * (1 - (Math.abs(reqRow - 1 - pos) * 1.00 / matchingPair.numCandidateTokens()));
                    }
                    else
                    {
                        score += -idf;
                    }
                }
                reqRow--;
                candCol--;
                break;
            case DELETE:
                int pos = matchingPair.findTokenPosInCandidate(reqRow - 1);
                idf = matchingPair.idfRequestTokenAt(reqRow - 1);
                if (pos != -1)
                {
                    fracMatched = matchingPair.fracMatchingChars(reqRow - 1, pos);
                    score += fracMatched * idf * (1 - (Math.abs(reqRow - 1 - pos) * 1.00 / matchingPair.numCandidateTokens()));
                }
                else
                {
                    score += -1.0;
                }

                reqRow--;
                break;
            case INSERT:// since we are only searching for the request token in the candidate, in this case we do not need to search as the request token is a
                        // Gap.
                score += -1.0;
                candCol--;
                break;
            default:
                throw new IllegalStateException();
            }

        }
        return score;
    }

    private static class DoubleMatrix
    {
        private final int numRows;
        private final int numCols;
        private final double[] values;

        private DoubleMatrix(int numRows, int numCols)
        {
            this.numRows = numRows;
            this.numCols = numCols;
            this.values = new double[numRows * numCols];
        }

        public double c(int row, int col)
        {
            return values[_idx(row, col)];
        }

        private int _idx(int row, int col)
        {
            return numCols * row + col;
        }

        public void set(int row, int col, double val)
        {
            values[_idx(row, col)] = val;
        }

        @Override
        public String toString()
        {
            int maxSize = (int) Math.max(Math.log10(Doubles.max(values) + 1), 1 + Math.log10(Math.min(0, Doubles.min(values)) + 1));
            StringBuilder sb = new StringBuilder((maxSize + 1) * numCols + 4 * numRows);
            Formatter f = new Formatter(sb);
            String fmt = "%" + maxSize + "d ";
            for (int row = 0; row < numRows; row++)
            {
                sb.append("[ ");
                for (int col = 0; col < numCols; col++)
                {
                    f.format(fmt, c(row, col));
                }

                sb.append("]\n");
            }
            f.close();
            return sb.toString();
        }

    }

    private static class IntMatrix
    {
        private final int numRows;
        private final int numCols;
        private final int[] values;

        private IntMatrix(int numRows, int numCols)
        {
            this.numRows = numRows;
            this.numCols = numCols;
            this.values = new int[numRows * numCols];
        }

        public int c(int row, int col)
        {
            return values[_idx(row, col)];
        }

        private int _idx(int row, int col)
        {
            return numCols * row + col;
        }

        public void set(int row, int col, int val)
        {
            values[_idx(row, col)] = val;
        }

        @Override
        public String toString()
        {
            int maxSize = (int) Math.max(Math.log10(Ints.max(values) + 1), 1 + Math.log10(Math.min(0, Ints.min(values)) + 1));
            StringBuilder sb = new StringBuilder((maxSize + 1) * numCols + 4 * numRows);
            Formatter f = new Formatter(sb);
            String fmt = "%" + maxSize + "d ";
            for (int row = 0; row < numRows; row++)
            {
                sb.append("[ ");
                for (int col = 0; col < numCols; col++)
                {
                    f.format(fmt, c(row, col));
                }

                sb.append("]\n");
            }
            f.close();
            return sb.toString();
        }
    }

}
