package marvin.com.br.tiraentulho;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.room.Room;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import marvin.com.br.tiraentulho.DAO.AppDatabase;
import marvin.com.br.tiraentulho.model.PercursoModel;

public class MapaRotaActivity extends AppCompatActivity {

    private TextView txtInfo;
    private String uuidRota;
    private AppDatabase db;
    private WebView webView;

    private final ExecutorService io =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mapa_rota);

        txtInfo = findViewById(R.id.txt_titulo);

        configurarMapa();

        // UUID recebido da tela anterior
        uuidRota = getIntent()
                .getStringExtra("uuid_rota");

        if (uuidRota == null || uuidRota.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Rota inválida.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

       // configurarMapa();

        // use aqui a mesma forma que você já usa
        // para obter sua instância do Room
        try {
            db = Room.databaseBuilder(this, AppDatabase.class, "app-db")
                    .fallbackToDestructiveMigration()
                    .build();

        } catch (Exception e) {
            Toast.makeText(this, "Errro com bd "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        findViewById(R.id.btn_fechar)
                .setOnClickListener(v -> finish());


        carregarPercurso();
    }

    private void configurarMapa() {

        FrameLayout containerMapa =
                findViewById(R.id.container_mapa);

        webView = new WebView(this);

        containerMapa.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        String html =
                "<!DOCTYPE html>" +
                        "<html>" +
                        "<head>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +

                        "<link rel='stylesheet' " +
                        "href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +

                        "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +

                        "<style>" +
                        "html, body { margin:0; padding:0; width:100%; height:100%; }" +
                        "#map { width:100%; height:100%; }" +
                        "</style>" +

                        "</head>" +

                        "<body>" +

                        "<div id='map'></div>" +

                        "<script>" +

                        "var map = L.map('map').setView([-15.55,-55.16], 14);" +

                        "L.tileLayer(" +
                        "'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'," +
                        "{maxZoom:19}" +
                        ").addTo(map);" +

                        "</script>" +

                        "</body>" +
                        "</html>";

        webView.setWebViewClient(new WebViewClient());

        webView.loadDataWithBaseURL(
                "https://localhost/",
                html,
                "text/html",
                "UTF-8",
                null
        );
    }

    private void carregarPercurso() {

        io.execute(() -> {

            try {

                List<PercursoModel> percurso =
                        db.percursoDAO()
                                .pegarPercursoRota(uuidRota);

                runOnUiThread(() -> {

                    if (percurso == null ||
                            percurso.isEmpty()) {

                        txtInfo.setText(
                                "Nenhum ponto de GPS encontrado."
                        );

                        Toast.makeText(
                                MapaRotaActivity.this,
                                "Não há percurso registrado para esta rota.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }
                    prepararPercurso(percurso);
                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    txtInfo.setText(
                            "Erro ao carregar percurso."
                    );

                    Toast.makeText(
                            MapaRotaActivity.this,
                            "Erro: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void prepararPercurso(List<PercursoModel> percurso) {

        if (webView == null || percurso == null || percurso.isEmpty()) {
            return;
        }

        StringBuilder pontos = new StringBuilder();
        pontos.append("[");

        boolean primeiro = true;

        for (PercursoModel p : percurso) {

            if (p.lat == null || p.lon == null) {
                continue;
            }

            // ignora coordenadas inválidas
            if (p.lat < -90 || p.lat > 90 ||
                    p.lon < -180 || p.lon > 180) {
                continue;
            }

            if (!primeiro) {
                pontos.append(",");
            }

            pontos.append("[")
                    .append(p.lat)
                    .append(",")
                    .append(p.lon)
                    .append("]");

            primeiro = false;
        }

        pontos.append("]");

        String javascript =
                "var pontos = " + pontos + ";" +

                        "if (pontos.length > 0) {" +

                        // desenha o trajeto
                        "var linha = L.polyline(pontos, {" +
                        "color: 'blue'," +
                        "weight: 6," +
                        "opacity: 0.8" +
                        "}).addTo(map);" +

                        // marcador inicial
                        "L.marker(pontos[0])" +
                        ".addTo(map)" +
                        ".bindPopup('Início da rota');" +

                        // marcador final
                        "L.marker(pontos[pontos.length - 1])" +
                        ".addTo(map)" +
                        ".bindPopup('Fim da rota');" +

                        // enquadra automaticamente todo o percurso
                        "map.fitBounds(linha.getBounds(), {" +
                        "padding: [30,30]" +
                        "});" +

                        "}";

        webView.evaluateJavascript(javascript, null);

    }

    @Override
    protected void onDestroy() {

        io.shutdown();

        super.onDestroy();
    }
}