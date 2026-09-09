package marvin.com.br.tiraentulho.DAO;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

import marvin.com.br.tiraentulho.model.PercursoModel;
import marvin.com.br.tiraentulho.model.RotaModel;

@Dao
public interface  PercursoDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long inserir(PercursoModel a);

    @Update
    void updateAll(List<PercursoModel> rotas);

    @Query("SELECT * FROM percurso_model WHERE uuid_rota = :uuidRota")
    List<PercursoModel> pegar_percurso_rota(String uuidRota);

    @Query("DELETE FROM percurso_model  WHERE uuid_rota = :uuidRota")
    void apagar_percurso_rota(String uuidRota);

    @Query("SELECT * FROM percurso_model WHERE uuid_rota = :uuidRota ORDER BY id ASC")
    List<PercursoModel> pegarPercursoRota(String uuidRota);

    @Query("SELECT * FROM percurso_model WHERE sincronizado = 0")
    List<PercursoModel> pegarPercursoRotaSincronizar();

    @Query("SELECT * FROM percurso_model")
    List<PercursoModel> pegarTodosPercurso();

}
