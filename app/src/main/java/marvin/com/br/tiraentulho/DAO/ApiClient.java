package marvin.com.br.tiraentulho.DAO;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit;

    private static String url_dev = "http://172.20.121.51:8008";
    private static String url_prefeitura = "http://24.152.35.148:8008";
    private static String url_parceiro = "";

    public static Retrofit getClient(Context context) {

        Gson gson = new GsonBuilder().setLenient().create();

        if (retrofit == null) {

            retrofit = new Retrofit.Builder()
                    .baseUrl(url_dev)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}