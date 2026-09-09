package marvin.com.br.tiraentulho.DAO;


import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import marvin.com.br.tiraentulho.model.ConfigModel;
import marvin.com.br.tiraentulho.model.PercursoModel;
import marvin.com.br.tiraentulho.model.RotaModel;


@Database(entities = {RotaModel.class, ConfigModel.class, PercursoModel.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract RotaDAO rotaDAO();
    public abstract ConfigDAO configDAO();
    public abstract PercursoDAO percursoDAO();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 10;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "tira_entulho")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
