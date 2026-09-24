package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFiltersTest {

    @Test
    fun `el valor inicial no tiene filtros activos`() {
        assertEquals(0, FeedFilters.DEFAULT.activeCount)
        assertTrue(FeedFilters.DEFAULT.isDefault)
    }

    @Test
    fun `cada categoría, «Cercanos» y «Solo verificados» cuentan como un filtro`() {
        val filters = FeedFilters(setOf(Category.GASTRONOMY, Category.NATURE), LocationScope.NEARBY, verifiedOnly = true)

        assertEquals(4, filters.activeCount)
        assertFalse(filters.isDefault)
    }
}
