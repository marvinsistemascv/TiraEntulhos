package marvin.com.br.tiraentulho.DAO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import marvin.com.br.tiraentulho.model.ConfigModel;

@Dao
public interface ConfigDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void inserir(ConfigModel e);

    @Query("SELECT * FROM config_model where id =1")
    ConfigModel pegar_config();
}
