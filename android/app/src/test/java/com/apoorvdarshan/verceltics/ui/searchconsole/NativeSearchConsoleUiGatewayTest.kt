package com.apoorvdarshan.verceltics.ui.searchconsole

import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleAggregationType
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleAnalyticsResponse
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleAnalyticsRow
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleDataState
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleDimension
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleFailure
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleFailureKind
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleFetchResult
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleFilterDimension
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleFilterOperator
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleSearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSearchConsoleUiGatewayTest {
    @Test
    fun performanceQueryMapsEveryGoogleControlWithoutSecrets() {
        val query = SearchConsolePerformanceQueryUi(
            preset = SearchConsoleDatePresetUi.CUSTOM,
            startDate = "2026-01-02",
            endDate = "2026-03-04",
            searchType = SearchConsoleSearchTypeUi.IMAGE,
            dataState = SearchConsoleDataStateUi.HOURLY_ALL,
            aggregation = SearchConsoleAggregationUi.BY_PAGE,
            dimensions = listOf(SearchConsoleDimensionUi.PAGE, SearchConsoleDimensionUi.COUNTRY),
            filters = listOf(
                SearchConsoleFilterUi(
                    SearchConsoleDimensionUi.QUERY,
                    SearchConsoleFilterOperatorUi.INCLUDING_REGEX,
                    "swift|compose",
                ),
            ),
            sortField = SearchConsoleSortFieldUi.IMPRESSIONS,
            pageSize = 50,
        )

        val mapped = query.toDataQuery(
            dimensions = listOf(SearchConsoleDimension.PAGE, SearchConsoleDimension.COUNTRY),
            rowLimit = 25_000,
        )

        assertEquals("2026-01-02", mapped.dateRange.startDate)
        assertEquals("2026-03-04", mapped.dateRange.endDate)
        assertEquals(SearchConsoleSearchType.IMAGE, mapped.searchType)
        assertEquals(SearchConsoleDataState.HOURLY_ALL, mapped.dataState)
        assertEquals(SearchConsoleAggregationType.BY_PAGE, mapped.aggregationType)
        assertEquals(listOf(SearchConsoleDimension.PAGE, SearchConsoleDimension.COUNTRY), mapped.dimensions)
        assertEquals(SearchConsoleFilterDimension.QUERY, mapped.dimensionFilterGroups.single().filters.single().dimension)
        assertEquals(SearchConsoleFilterOperator.INCLUDING_REGEX, mapped.dimensionFilterGroups.single().filters.single().operator)
        assertFalse(mapped.toString().contains("access_token"))
    }

    @Test
    fun combinedPerformanceSortsPagesAndKeepsPartialWarning() {
        val timeline = SearchConsoleFetchResult.Complete(
            SearchConsoleAnalyticsResponse(
                rows = listOf(
                    SearchConsoleAnalyticsRow(listOf("2026-08-01"), 2.0, 20.0, 0.1, 5.0),
                    SearchConsoleAnalyticsRow(listOf("2026-08-02"), 3.0, 30.0, 0.1, 4.0),
                ),
                responseAggregationType = "auto",
                metadata = null,
            ),
        )
        val breakdown = SearchConsoleFetchResult.Partial(
            value = SearchConsoleAnalyticsResponse(
                rows = (0 until 30).map { index ->
                    SearchConsoleAnalyticsRow(
                        keys = listOf("query-$index"),
                        clicks = index.toDouble(),
                        impressions = (index * 10).toDouble(),
                        ctr = 0.1,
                        position = index.toDouble(),
                    )
                },
                responseAggregationType = "auto",
                metadata = null,
            ),
            failure = SearchConsoleFailure(
                SearchConsoleFailureKind.LIMIT_REACHED,
                "Bounded test result.",
            ),
        )
        val query = SearchConsolePerformanceQueryUi.default().copy(page = 1, pageSize = 10)

        val result = combinePerformance(timeline, breakdown, query)
        val available = result as SearchConsoleResourceUi.Available

        assertTrue(available.isPartial)
        assertEquals("query-19", available.value.breakdownRows.first().keys.single())
        assertEquals("query-10", available.value.breakdownRows.last().keys.single())
        assertTrue(available.value.hasPreviousPage)
        assertTrue(available.value.hasNextPage)
        assertEquals(5.0, available.value.clicks, 0.0)
    }
}
