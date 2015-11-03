/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.suggest.completion.context;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.GeoUtils;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.search.suggest.completion.context.GeoContextMapping.*;

/**
 * Defines the query context for {@link GeoContextMapping}
 */
public final class GeoQueryContext implements ToXContent {
    public CharSequence geoHash;
    public int boost = 1;
    public int precision = DEFAULT_PRECISION;
    public int[] neighbours;

    /**
     * Creates a query context for a given geo point with a boost of 1
     * and a precision of {@value GeoContextMapping#DEFAULT_PRECISION}
     */
    public GeoQueryContext(GeoPoint geoPoint) {
        this(geoPoint.geohash());
    }

    /**
     * Creates a query context for a given geo point with a
     * provided boost
     */
    public GeoQueryContext(GeoPoint geoPoint, int boost) {
        this(geoPoint.geohash(), boost);
    }

    /**
     * Creates a query context with a given geo hash with a boost of 1
     * and a precision of {@value GeoContextMapping#DEFAULT_PRECISION}
     */
    public GeoQueryContext(CharSequence geoHash) {
        this(geoHash, 1);
    }

    /**
     * Creates a query context for a given geo hash with a
     * provided boost
     */
    public GeoQueryContext(CharSequence geoHash, int boost) {
        this(geoHash, boost, DEFAULT_PRECISION);
    }

    /**
     * Creates a query context for a geo point with
     * a provided boost and enables generating neighbours
     * at specified precisions
     */
    public GeoQueryContext(GeoPoint geoPoint, int boost, int precision, int... neighbours) {
        this(geoPoint.geohash(), boost, precision, neighbours);
    }

    /**
     * Creates a query context for a geo hash with
     * a provided boost and enables generating neighbours
     * at specified precisions
     */
    public GeoQueryContext(CharSequence geoHash, int boost, int precision, int... neighbours) {
        this.geoHash = geoHash;
        this.boost = boost;
        this.precision = precision;
        this.neighbours = neighbours;
    }

    private GeoQueryContext() {
    }

    void setBoost(int boost) {
        this.boost = boost;
    }

    void setPrecision(int precision) {
        this.precision = precision;
    }

    void setNeighbours(List<Integer> neighbours) {
        int[] neighbourArray = new int[neighbours.size()];
        for (int i = 0; i < neighbours.size(); i++) {
            neighbourArray[i] = neighbours.get(i);
        }
        this.neighbours = neighbourArray;
    }

    private GeoPoint point;
    void setPoint(GeoPoint point) {
        this.point = point;
    }

    private double lat = Double.NaN;
    void setLat(double lat) {
        this.lat = lat;
    }

    private double lon = Double.NaN;
    void setLon(double lon) {
        this.lon = lon;
    }

    void setGeoHash(String geoHash) {
        this.geoHash = geoHash;
    }

    void finish() {
        if (point == null) {
            if (Double.isNaN(lat) == false && Double.isNaN(lon) == false) {
                point = new GeoPoint(lat, lon);
            } else {
                throw new ElasticsearchParseException("no geohash or geo point provided");
            }
        }
        this.geoHash = point.geohash();
        if (this.neighbours == null) {
            this.neighbours = new int[]{precision};
        }
    }

    private static ObjectParser<GeoQueryContext, GeoContextMapping> GEO_CONTEXT_PARSER = new ObjectParser<>("geo", null);
    static {
        GEO_CONTEXT_PARSER.declareField((parser, geoQueryContext, geoContextMapping) -> geoQueryContext.setPoint(GeoUtils.parseGeoPoint(parser)), new ParseField("context"), ObjectParser.ValueType.OBJECT);
        GEO_CONTEXT_PARSER.declareString(GeoQueryContext::setGeoHash, new ParseField("context"));
        GEO_CONTEXT_PARSER.declareInt(GeoQueryContext::setBoost, new ParseField("boost"));
        // TODO : add string support for precision for GeoUtils.geoHashLevelsForPrecision()
        GEO_CONTEXT_PARSER.declareInt(GeoQueryContext::setPrecision, new ParseField("precision"));
        // TODO : add string array support for precision for GeoUtils.geoHashLevelsForPrecision()
        GEO_CONTEXT_PARSER.declareIntArray(GeoQueryContext::setNeighbours, new ParseField("neighbours"));
        GEO_CONTEXT_PARSER.declareDouble(GeoQueryContext::setLat, new ParseField("lat"));
        GEO_CONTEXT_PARSER.declareDouble(GeoQueryContext::setLon, new ParseField("lon"));
    }

    public static GeoQueryContext parse(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();
        GeoQueryContext queryContext = new GeoQueryContext();
        if (token == XContentParser.Token.START_OBJECT) {
            GEO_CONTEXT_PARSER.parse(parser, queryContext);
        } else if (token == XContentParser.Token.VALUE_STRING) {
            queryContext.setPoint(GeoPoint.fromGeohash(parser.text()));
        } else {
            throw new ElasticsearchParseException("geo context must be an object or string");
        }
        queryContext.finish();
        return queryContext;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startObject(CONTEXT_VALUE);
        builder.field("geohash", geoHash);
        builder.endObject();
        builder.field(CONTEXT_BOOST, boost);
        builder.field(CONTEXT_NEIGHBOURS, neighbours);
        builder.field(CONTEXT_PRECISION, precision);
        builder.endObject();
        return builder;
    }
}