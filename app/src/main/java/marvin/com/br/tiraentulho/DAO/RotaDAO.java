package marvin.com.br.tiraentulho.DAO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import marvin.com.br.tiraentulho.model.RotaModel;

@Dao
public interface RotaDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long inserir(RotaModel a);

    @Update
    void updateAll(List<RotaModel> rotas);

    @Query("SELECT * FROM rota_model ORDER BY id DESC")
    List<RotaModel> pegar_rotas_realizadas();

    @Query("SELECT * FROM rota_model where sit = 'EM ANDAMENTO'")
    RotaModel pegar_rotas_ativa();

    @Query("DELETE FROM rota_model WHERE sit = 'SINCRONIZADO' ")
    void apagar_sincronizados();
}
