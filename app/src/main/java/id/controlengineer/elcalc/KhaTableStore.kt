package id.controlengineer.elcalc

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.controlengineer.elcalc.core.KhaTable
import id.controlengineer.elcalc.core.KhaTableCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.khaDataStore: DataStore<Preferences> by preferencesDataStore(name = "kha_tables")

/** Tabel KHA yang diisi pengguna sendiri (offline, tanpa menyalin tabel PUIL/IEC ke dalam app) + tabel terpilih. */
class KhaTableStore(context: Context) {
  private val store = context.applicationContext.khaDataStore
  private val keyTables = stringPreferencesKey("tables")
  private val keySelected = stringPreferencesKey("selected")

  val tables: Flow<List<KhaTable>> = store.data.map { KhaTableCodec.decode(it[keyTables]) }
  val selectedName: Flow<String?> = store.data.map { it[keySelected] }

  /** Simpan tabel (ganti tabel lama bernama [oldName] bila diubah) lalu jadikan terpilih. */
  suspend fun save(table: KhaTable, oldName: String?) {
    store.edit { p ->
      val list = KhaTableCodec.decode(p[keyTables]).filterNot { it.name == oldName || it.name == table.name }
      p[keyTables] = KhaTableCodec.encode(list + table)
      p[keySelected] = table.name
    }
  }

  suspend fun delete(name: String) {
    store.edit { p ->
      p[keyTables] = KhaTableCodec.encode(KhaTableCodec.decode(p[keyTables]).filterNot { it.name == name })
      if (p[keySelected] == name) p.remove(keySelected)
    }
  }

  suspend fun select(name: String?) {
    store.edit { p -> if (name == null) p.remove(keySelected) else p[keySelected] = name }
  }

  /** Untuk tes UI: kosongkan semua tabel. */
  suspend fun clearAll() {
    store.edit { it.clear() }
  }
}
