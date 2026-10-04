package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.GeoPoint
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet

/**
 * SQL explícito con parámetros, para las consultas espaciales sobre places.location (PostGIS) que Exposed no mapea. Los
 * valores nunca se pegan en el texto. Corre en la transacción de quien llama.
 */
internal class Sql {
    private val text = StringBuilder()
    private val args = mutableListOf<Pair<IColumnType<*>, Any?>>()

    fun append(part: String) = apply { text.append(part) }

    fun param(type: IColumnType<*>, value: Any?) = apply {
        text.append('?')
        args += type to value
    }

    /** El punto como geografía, para medir en metros. */
    fun point(at: GeoPoint) = apply {
        append("ST_SetSRID(ST_MakePoint(").param(DoubleColumnType(), at.longitude).append(", ")
        param(DoubleColumnType(), at.latitude).append("), 4326)::geography")
    }

    fun <T> query(row: (ResultSet) -> T): List<T> =
        TransactionManager.current().exec(text.toString(), args, StatementType.SELECT) { rs ->
            buildList { while (rs.next()) add(row(rs)) }
        }.orEmpty()
}
