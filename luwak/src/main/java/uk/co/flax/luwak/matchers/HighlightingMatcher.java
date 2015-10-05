package uk.co.flax.luwak.matchers;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanExtractor;
import org.apache.lucene.search.spans.SpanRewriter;
import uk.co.flax.luwak.CandidateMatcher;
import uk.co.flax.luwak.DocumentBatch;
import uk.co.flax.luwak.MatcherFactory;

/*
 * Copyright (c) 2014 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * CandidateMatcher class that will return exact hit positions for all matching queries
 *
 * If a stored query cannot be rewritten so as to extract Spans, a {@link HighlightsMatch} object
 * with no Hit positions will be returned.
 */

public class HighlightingMatcher extends CandidateMatcher<HighlightsMatch> {

    private final SpanRewriter rewriter;

    public HighlightingMatcher(DocumentBatch docs, SpanRewriter rewriter) {
        super(docs);
        this.rewriter = rewriter;
    }

    @Override
    protected void doMatchQuery(String queryId, Query matchQuery, Map<String, String> metadata) throws IOException {
        HighlightsMatch match = doMatch(queryId, matchQuery);
        if (match != null)
            this.addMatch(match);
    }

    protected void addMatch(HighlightsMatch match) {
        HighlightsMatch previousMatch = this.matches(match.getDocId(), match.getDocId());
        if (previousMatch == null) {
            super.addMatch(match);
            return;
        }
        super.addMatch(HighlightsMatch.merge(match.getQueryId(), match.getDocId(), previousMatch, match));
    }

    public HighlightsMatch resolve(HighlightsMatch match1, HighlightsMatch match2) {
        return HighlightsMatch.merge(match1.getQueryId(), match1.getDocId(), match1, match2);
    }

    protected class HighlightCollector implements SpanCollector {

        HighlightsMatch match;
        final String queryId;

        public HighlightCollector(String queryId) {
            this.queryId = queryId;
        }

        void setMatch(int doc) {
            this.match = new HighlightsMatch(queryId, docs.resolveDocId(doc));
        }

        @Override
        public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
            match.addHit(term.field(), position, position, postings.startOffset(), postings.endOffset());
        }

        @Override
        public void reset() {

        }
    }

    protected HighlightsMatch findHighlights(String queryId, Query query) throws IOException {

        final HighlightCollector collector = new HighlightCollector(queryId);

        docs.getSearcher().search(rewriter.rewrite(query), new SimpleCollector() {

            Scorer scorer;

            @Override
            public void collect(int i) throws IOException {
                try {
                    collector.setMatch(i);
                    SpanExtractor.collect(scorer, collector, true);
                }
                catch (Exception e) {
                    collector.match.error = e.getMessage();
                }
            }

            @Override
            public void setScorer(Scorer scorer) throws IOException {
                this.scorer = scorer;
            }

            @Override
            public boolean needsScores() {
                return true;
            }
        });

        return collector.match;
    }

    protected HighlightsMatch doMatch(String queryId, Query query) throws IOException {
        if (docs.getSearcher().count(query) == 0)
            return null;
        return findHighlights(queryId, query);
    }

    public static final MatcherFactory<HighlightsMatch> FACTORY = new MatcherFactory<HighlightsMatch>() {
        @Override
        public HighlightingMatcher createMatcher(DocumentBatch docs) {
            return new HighlightingMatcher(docs, new SpanRewriter());
        }
    };

    public static MatcherFactory<HighlightsMatch> factory(final SpanRewriter rewriter) {
        return new MatcherFactory<HighlightsMatch>() {
            @Override
            public CandidateMatcher<HighlightsMatch> createMatcher(DocumentBatch docs) {
                return new HighlightingMatcher(docs, rewriter);
            }
        };
    }

}