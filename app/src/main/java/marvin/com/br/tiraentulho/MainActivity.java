package marvin.com.br.tiraentulho;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marvin.com.br.tiraentulho.DAO.ApiClient;
import marvin.com.br.tiraentulho.DAO.AppDatabase;
import marvin.com.br.tiraentulho.model.ConfigModel;
import marvin.com.br.tiraentulho.model.MotoristaModel;
import marvin.com.br.tiraentulho.model.PercursoModel;
import marvin.com.br.tiraentulho.model.RotaModel;
import marvin.com.br.tiraentulho.model.SincronizacaoRequest;
import marvin.com.br.tiraentulho.model.SincronizacaoResponse;
import marvin.com.br.tiraentulho.repository.RetroServiceInterface;
import marvin.com.br.tiraentulho.service.RastreamentoService;
import marvin.com.br.tiraentulho.util.DataHora;
import marvin.com.br.tiraentulho.util.MascaraCPF;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.room.Update;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private AlertDialog dialog;                         // <— diálogo atual
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean pediuCadastroEncarregado = false;   // evita abrir 2x
    private RetroServiceInterface service;
    Retrofit retrofit;
    private TextView txtVersao;
    private TextView txtMotorista;
    private TextView txtPlacaMelosa;
    private ConfigModel configuracao = new ConfigModel();

    private TextView txtRuaPercurso;
    private TextView txtBairroPercurso;
    private TextView txtCidadePercurso;
    private TextView txtStatusLocalizacao;
    private Button btnIniciarPercurso;
    private Double latitudeInicio;
    private Double longitudeInicio;
    private String ruaInicio;
    private String bairroInicio;
    private String cidadeInicio;
    private RotaModel rota_ativa;
    private Double latitudeFinal;
    private Double longitudeFinal;
    private String ruaFinal;
    private String bairroFinal;
    private String cidadeFinal;
    private Double kmPercorridoFinal = 0.0;

    private FusedLocationProviderClient fusedLocationClient;
    private static final int REQUEST_LOCATION = 100;

    private final ActivityResultLauncher<Intent> qrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String qr = result.getData().getStringExtra("qr_text");
                    Integer veiculoId = extrairIdVeiculo(qr);
                    if (veiculoId != null) {
                        MediaPlayer mp = MediaPlayer.create(this, R.raw.camera_som);
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();

                        View viewInflated = LayoutInflater.from(this)
                                .inflate(R.layout.dialog_novo_percurso, null);

                        ImageButton btnFecha = viewInflated.findViewById(R.id.btnFecharLcto);


                        txtPlacaMelosa.setText("" + veiculoId);

                        txtRuaPercurso =
                                viewInflated.findViewById(R.id.txtRuaPercurso);

                        txtBairroPercurso =
                                viewInflated.findViewById(R.id.txtBairroPercurso);

                        txtCidadePercurso =
                                viewInflated.findViewById(R.id.txtCidadePercurso);

                        txtStatusLocalizacao =
                                viewInflated.findViewById(R.id.txtStatusLocalizacao);

                        btnIniciarPercurso =
                                viewInflated.findViewById(R.id.btnIniciarPercurso);

                        dialog = new AlertDialog.Builder(this)
                                .setView(viewInflated)
                                .create();

                        dialog.show();

                        if (dialog.getWindow() != null) {
                            dialog.getWindow()
                                    .setBackgroundDrawable(
                                            new ColorDrawable(Color.TRANSPARENT)
                                    );
                        }

                        dialog.setCanceledOnTouchOutside(false);

                        btnFecha.setOnClickListener(v -> dialog.dismiss());

                        btnIniciarPercurso.setOnClickListener(v -> {
                            setar_veiculo_rota(
                                    String.valueOf(veiculoId)
                            );
                        });

                        verificarPermissaoLocalizacao();

                    } else {
                        Toast.makeText(this, "QR inválido", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private void setar_veiculo_rota(String veiculoId) {

        io.execute(() -> {

            try {

                ConfigModel config =
                        db.configDAO().pegar_config();

                if (config == null || config.motorista == null) {

                    runOnUiThread(() ->
                            showToastErro("App ainda não foi configurado!")
                    );

                    return;
                }

                config.veiculo = veiculoId.trim();

                // BANCO CONTINUA NA THREAD IO
                db.configDAO().inserir(config);

                configuracao = config;

                runOnUiThread(() -> {

                    txtPlacaMelosa.setText(config.veiculo);

                    // SOMENTE DEPOIS DO VEÍCULO ESTAR DEFINIDO
                    iniciarNovoPercurso();
                });

            } catch (Exception ex) {

                runOnUiThread(() ->
                        showToastErro(
                                "Erro ao salvar veículo: "
                                        + ex.getMessage()
                        )
                );
            }
        });
    }

    private void iniciarNovoPercurso() {

        if (latitudeInicio == null || longitudeInicio == null) {

            Toast.makeText(
                    this,
                    "Aguarde a localização ser identificada",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        io.execute(() -> {

            RotaModel rota = new RotaModel();

            String uuidRota = UUID.randomUUID().toString();

            rota.uuid_rota = uuidRota;
            rota.motorista = configuracao.motorista;
            rota.veiculo = configuracao.veiculo;

            rota.data_rota = DataHora.data_atual();
            rota.hora_rota = DataHora.pegar_hora();

            rota.sit = "EM ANDAMENTO";

            rota.tenant_id = configuracao.tenant_id;

            // se você adicionou esses campos
            rota.rua_icinio = ruaInicio;
            rota.bairro_inicio = bairroInicio;
            rota.cidade_inicio = cidadeInicio;

            rota.lat_inicio = latitudeInicio;
            rota.lon_inicio = longitudeInicio;

            db.rotaDAO().inserir(rota);

            verificar_rota_ativa();

            runOnUiThread(() -> {

                iniciarServicoRastreamento(rota.uuid_rota);

                dialog.dismiss();

                android.util.Log.e("TIRA_ENTULHO", "TESTE LOGCAT - PERCURSO INICIADO");

                Toast.makeText(
                        this,
                        "Percurso iniciado ",
                        Toast.LENGTH_SHORT
                ).show();

            });

        });
    }

    private void abrirDialogFinalizarRota() {

        if (rota_ativa == null) {
            showToastErro("Nenhuma rota em andamento.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            showToastErro("Permissão de localização não concedida.");
            return;
        }

        fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
        ).addOnSuccessListener(location -> {

            if (location == null) {
                showToastErro("Não foi possível obter a localização atual.");
                return;
            }

            latitudeFinal = location.getLatitude();
            longitudeFinal = location.getLongitude();

            android.util.Log.e(
                    "TIRA_ENTULHO",
                    "LOCAL FINAL -> "
                            + latitudeFinal
                            + " / "
                            + longitudeFinal
                            + " precisão: "
                            + location.getAccuracy()
            );

            buscarEnderecoFinal(
                    latitudeFinal,
                    longitudeFinal
            );
        });
    }

    private void iniciarServicoRastreamento(String uuidRota) {

        Intent intent = new Intent(
                this,
                RastreamentoService.class
        );

        intent.putExtra("uuid_rota", uuidRota);
        intent.putExtra("tenant_id", configuracao.tenant_id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void iniciarRetrofit() {
        retrofit = ApiClient.getClient(this);
        service = retrofit.create(RetroServiceInterface.class);
        Toast.makeText(this, "Retrofit iniciado", Toast.LENGTH_SHORT).show();
    }

    public void verificar_rota_ativa() {


        io.execute(() -> {

            try {
                db = Room.databaseBuilder(MainActivity.this, AppDatabase.class, "app-db")
                        .fallbackToDestructiveMigration()
                        .build();

            } catch (Exception e) {
                showToastErro("ERRO DB: " + e.getMessage());
            }


            try {
                RotaModel r_ativa = db.rotaDAO().pegar_rotas_ativa();

                runOnUiThread(() -> {
                    if (r_ativa == null) {
                        rota_ativa = null;
                    } else if (r_ativa != null) {
                        rota_ativa = r_ativa;
                        showToastSucess("ROTA EM ANDAMENTO");
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() ->
                        showToastErro("Erro ao pegar configuração: " + ex.getMessage())
                );
            }
        });

    }

    public void pegar_configuracao() {

        io.execute(() -> {

            try {
                db = Room.databaseBuilder(MainActivity.this, AppDatabase.class, "app-db")
                        .fallbackToDestructiveMigration()
                        .build();

            } catch (Exception e) {
                showToastErro("ERRO DB: " + e.getMessage());
            }


            try {
                ConfigModel config = db.configDAO().pegar_config();

                runOnUiThread(() -> {
                    if (config == null || config.motorista == null) {
                        abrirDialogConfiguracaoInicial();
                    } else if (config.motorista != null && !config.motorista.trim().isEmpty()) {
                        this.configuracao = config;

                        txtMotorista = findViewById(R.id.txt_nome_motorista);
                        txtPlacaMelosa = findViewById(R.id.txt_placa_melosa);

                        txtMotorista.setText(configuracao.motorista);
                        txtPlacaMelosa.setText(configuracao.veiculo);

                        iniciarRetrofit();
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() ->
                        showToastErro("Erro ao pegar configuração: " + ex.getMessage())
                );
            }
        });
    }

    private void abrirDialogConfiguracaoInicial() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_insere_cpf_operador, null);

        EditText cpf = dialogView.findViewById(R.id.inputCpfOperadorConfiguracao);
        Button btnSalvar = dialogView.findViewById(R.id.btnGravaCpfOperador);
        cpf.addTextChangedListener(new MascaraCPF(cpf));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSalvar.setOnClickListener(v -> {
            String cpfTexto = cpf.getText().toString().trim();

            if (cpfTexto.isEmpty()) {
                cpf.setError("Informe o CPF");
                cpf.requestFocus();
                return;
            }

            ConfigModel config = new ConfigModel();
            config.cpf_motorista = cpfTexto;

            io.execute(() -> {
                try {
                    db.configDAO().inserir(config);
                    runOnUiThread(() -> {
                        //url_servidor = urlTexto;
                        iniciarRetrofit();
                        dialog.dismiss();
                        pegar_operador_cpf_api(cpfTexto);
                    });

                } catch (Exception e) {
                    runOnUiThread(() ->
                            showToastErro("Erro ao salvar configuração: " + e.getMessage())
                    );
                }
            });
        });
    }

    public void pegar_operador_cpf_api(String cpf) {

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Aguarde buscando operador...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {

            Call<MotoristaModel> call = service.pegar_motorista_cpf(cpf.replaceAll("[^\\d]", ""));
            call.enqueue(new Callback<MotoristaModel>() {
                @Override
                public void onResponse(Call<MotoristaModel> call, Response<MotoristaModel> response) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (response.isSuccessful()) {
                            new Thread(() -> {
                                MotoristaModel operador = response.body();
                                gravar_dados_motorista_local(operador);
                            }).start();

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                            });
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("ERRO");
                            builder.setMessage(response.code());
                            builder.setIcon(R.drawable.ic_erro);
                            builder.setPositiveButton("OK", null);
                            builder.create().show();
                        }
                    });
                }

                @Override
                public void onFailure(Call<MotoristaModel> call, Throwable t) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Falha na consulta: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        }).start();
    }

    public void gravar_dados_motorista_local(MotoristaModel m) {

        io.execute(() -> {
            try {
                ConfigModel config = db.configDAO().pegar_config();

                if (config == null) {
                    config = new ConfigModel();
                }


                config.cpf_motorista = m.cpf;
                config.motorista = m.nome;
                config.tenant_id = m.tenant_id;

                db.configDAO().inserir(config);

                runOnUiThread(() -> {
                    txtMotorista.setText(m.nome);
                    iniciarRetrofit();
                    Toast.makeText(getApplicationContext(), "Motorista encontrado!", Toast.LENGTH_LONG).show();

                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        showToastErro("Erro ao salvar configuração: " + e.getMessage())
                );
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        ImageView btn_rota = findViewById(R.id.btn_brasao);

        this.setTitle("Home");


        txtPlacaMelosa = findViewById(R.id.txt_placa_melosa);
        txtMotorista = findViewById(R.id.txt_nome_motorista);

        btn_rota.setOnClickListener(v -> {

            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            tocarClique();
            if (rota_ativa == null) {

                qrLauncher.launch(
                        new Intent(this, QrScannerActivity.class)
                );

            } else {
                abrirDialogFinalizarRota();
            }
        });
        pegar_configuracao();
        verificar_rota_ativa();
    }

    private void pegarLocalizacaoAtual() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        txtStatusLocalizacao.setText("Obtendo localização...");

        btnIniciarPercurso.setEnabled(false);

        fusedLocationClient
                .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                )
                .addOnSuccessListener(location -> {

                    if (location != null) {

                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();

                        latitudeInicio = latitude;
                        longitudeInicio = longitude;

                        buscarEndereco(latitude, longitude);

                    } else {

                        latitudeInicio = 0.0;
                        longitudeInicio = 0.0;

                        Toast.makeText(
                                this,
                                "Não foi possível obter a localização",
                                Toast.LENGTH_LONG
                        ).show();
                    }

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Erro ao obter localização: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    private void buscarEndereco(double latitude, double longitude) {

        Geocoder geocoder =
                new Geocoder(
                        this,
                        new Locale("pt", "BR")
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    new Geocoder.GeocodeListener() {

                        @Override
                        public void onGeocode(
                                @NonNull List<Address> addresses) {

                            runOnUiThread(() -> {

                                if (!addresses.isEmpty()) {

                                    preencherEndereco(
                                            addresses.get(0)
                                    );

                                } else {
                                    showToastErro("Endereço não encontrado");
                                }

                            });
                        }

                        @Override
                        public void onError(
                                String errorMessage) {

                            runOnUiThread(() ->
                                    showToastErro("Endereço não encontrado")
                            );
                        }
                    }
            );

        } else {

            new Thread(() -> {

                try {

                    List<Address> addresses =
                            geocoder.getFromLocation(
                                    latitude,
                                    longitude,
                                    1
                            );

                    runOnUiThread(() -> {

                        if (addresses != null
                                && !addresses.isEmpty()) {

                            preencherEndereco(
                                    addresses.get(0)
                            );

                        } else {

                            showToastErro("Endereço não encontrado");

                        }

                    });

                } catch (IOException e) {

                    runOnUiThread(() ->
                            showToastErro("Endereço não encontrado")
                    );

                }

            }).start();
        }
    }

    private void buscarEnderecoFinal(double latitude, double longitude) {

        io.execute(() -> {

            try {

                Geocoder geocoder =
                        new Geocoder(
                                MainActivity.this,
                                new Locale("pt", "BR")
                        );

                List<Address> enderecos =
                        geocoder.getFromLocation(
                                latitude,
                                longitude,
                                1
                        );

                if (enderecos != null && !enderecos.isEmpty()) {

                    Address endereco = enderecos.get(0);

                    ruaFinal =
                            endereco.getThoroughfare() != null
                                    ? endereco.getThoroughfare()
                                    : "RUA NÃO IDENTIFICADA";

                    bairroFinal =
                            endereco.getSubLocality() != null
                                    ? endereco.getSubLocality()
                                    : "BAIRRO NÃO IDENTIFICADO";

                    cidadeFinal = pegarCidade(endereco);

                } else {

                    ruaFinal = "RUA NÃO IDENTIFICADA";
                    bairroFinal = "BAIRRO NÃO IDENTIFICADO";
                    cidadeFinal = "CIDADE NÃO IDENTIFICADA";
                }

                runOnUiThread(() -> {

                    android.util.Log.e(
                            "TIRA_ENTULHO",
                            "FINAL -> "
                                    + ruaFinal
                                    + " | "
                                    + bairroFinal
                                    + " | "
                                    + cidadeFinal
                    );

                    prepararDialogFinalizarRota();
                });

            } catch (Exception e) {

                ruaFinal = "RUA NÃO IDENTIFICADA";
                bairroFinal = "BAIRRO NÃO IDENTIFICADO";
                cidadeFinal = "CIDADE NÃO IDENTIFICADA";

                runOnUiThread(() ->
                        prepararDialogFinalizarRota()
                );
            }
        });
    }

    private String pegarCidade(Address endereco) {

        // Primeiro tenta Locality
        if (endereco.getLocality() != null
                && !endereco.getLocality().trim().isEmpty()) {

            return endereco.getLocality();
        }

        // Em alguns aparelhos, como Xiaomi,
        // o município pode vir em SubAdminArea
        if (endereco.getSubAdminArea() != null
                && !endereco.getSubAdminArea().trim().isEmpty()) {

            return endereco.getSubAdminArea();
        }

        return "CIDADE NÃO IDENTIFICADA";
    }

    private void preencherEndereco(Address endereco) {

        String rua = endereco.getThoroughfare();

        String bairro = endereco.getSubLocality();

        String cidade = endereco.getSubAdminArea();

        String estado = endereco.getAdminArea();


        if (rua == null || rua.trim().isEmpty()) {
            rua = "RUA NÃO IDENTIFICADA";
        }

        if (bairro == null || bairro.trim().isEmpty()) {
            bairro = "BAIRRO NÃO IDENTIFICADO";
        }

        if (cidade == null || cidade.trim().isEmpty()) {
            cidade = "CIDADE NÃO IDENTIFICADA";
        }


        ruaInicio = rua.toUpperCase();

        bairroInicio = bairro.toUpperCase();

        cidadeInicio = cidade.toUpperCase();


        txtRuaPercurso.setText(
                ruaInicio
        );

        txtBairroPercurso.setText(
                bairroInicio
        );

        txtCidadePercurso.setText(
                cidadeInicio +
                        (estado != null
                                ? " - " + estado.toUpperCase()
                                : "")
        );


        txtStatusLocalizacao.setText(
                "📍 Localização encontrada"
        );


        btnIniciarPercurso.setEnabled(true);

    }

    private void verificarPermissaoLocalizacao() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION
            );

        } else {

            pegarLocalizacaoAtual();

        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQUEST_LOCATION) {

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                pegarLocalizacaoAtual();

            } else {

                Toast.makeText(
                        this,
                        "A localização é necessária para identificar o bairro",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Nullable
    public static Integer extrairIdVeiculo(String url) {
        if (url == null) return null;
        Pattern p = Pattern.compile("/home/(\\d+)(?:/|\\?|#|$)");
        Matcher m = p.matcher(url.trim());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private void prepararDialogFinalizarRota() {

        if (rota_ativa == null) {
            showToastErro("Nenhuma rota em andamento.");
            return;
        }

        io.execute(() -> {

            List<PercursoModel> pontos =
                    db.percursoDAO()
                            .pegarPercursoRota(
                                    rota_ativa.uuid_rota
                            );

            kmPercorridoFinal =
                    calcularKmPercorrido(pontos);

            runOnUiThread(() ->
                    mostrarDialogFinalizarRota()
            );
        });
    }

    private void mostrarDialogFinalizarRota() {

        if (rota_ativa == null) {
            showToastErro("Nenhuma rota em andamento.");
            return;
        }
        // TESTE - VER O QUE REALMENTE TEM NA ROTA ATIVA
        android.util.Log.e(
                "TIRA_ENTULHO",
                "ROTA ATIVA -> "
                        + "id=" + rota_ativa.id
                        + " | uuid=" + rota_ativa.uuid_rota
                        + " | cod_veiculo=" + rota_ativa.veiculo
                        + " | placa=" + rota_ativa.veiculo
                        + " | nome_motorista=" + rota_ativa.motorista
                        + " | motorista=" + rota_ativa.motorista
        );


        View view = getLayoutInflater().inflate(
                R.layout.dialog_finalizar_percurso,
                null
        );

        TextView txtVeiculo =
                view.findViewById(R.id.txtFinalVeiculo);

        TextView txtMotorista =
                view.findViewById(R.id.txtFinalMotorista);

        TextView txtInicio =
                view.findViewById(R.id.txtFinalInicio);

        TextView txtDestino =
                view.findViewById(R.id.txtFinalDestino);

        Button btnFinalizar =
                view.findViewById(R.id.btnConfirmarFinalizacao);

        Button btnCancelar =
                view.findViewById(R.id.btnCancelarFinalizacao);


        // -----------------------------
        // VEÍCULO
        // -----------------------------

        txtVeiculo.setText(
                rota_ativa.veiculo != null
                        ? rota_ativa.veiculo
                        : "VEÍCULO NÃO IDENTIFICADO"
        );


        // -----------------------------
        // MOTORISTA
        // -----------------------------

        txtMotorista.setText(
                rota_ativa.motorista != null
                        ? rota_ativa.motorista
                        : "MOTORISTA NÃO IDENTIFICADO"
        );


        // -----------------------------
        // INÍCIO
        // -----------------------------

        String inicio =
                valorOuTraco(rota_ativa.rua_icinio)
                        + "\n"
                        + valorOuTraco(rota_ativa.bairro_inicio)
                        + " - "
                        + valorOuTraco(rota_ativa.cidade_inicio);

        txtInicio.setText(inicio);


        // -----------------------------
        // FINAL
        // -----------------------------

        String destino =
                valorOuTraco(ruaFinal)
                        + "\n"
                        + valorOuTraco(bairroFinal)
                        + " - "
                        + valorOuTraco(cidadeFinal);

        txtDestino.setText(destino);


        // -----------------------------
        // CRIA DIALOG
        // -----------------------------

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(view)
                        .setCancelable(false)
                        .create();


        // -----------------------------
        // CANCELAR
        // -----------------------------

        btnCancelar.setOnClickListener(v -> {

            v.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY
            );

            tocarClique();

            dialog.dismiss();
        });


        // -----------------------------
        // FINALIZAR
        // -----------------------------

        btnFinalizar.setOnClickListener(v -> {

            v.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY
            );

            tocarClique();

            dialog.dismiss();

            confirmarFinalizacaoRota();
        });


        dialog.show();
    }

    private String valorOuTraco(String valor) {

        if (valor == null || valor.trim().isEmpty()) {
            return "-";
        }

        return valor;
    }

    private double calcularKmPercorrido(List<PercursoModel> pontos) {

        if (pontos == null || pontos.size() < 2) {
            return 0.0;
        }

        double totalMetros = 0.0;

        for (int i = 1; i < pontos.size(); i++) {

            PercursoModel anterior = pontos.get(i - 1);
            PercursoModel atual = pontos.get(i);

            float[] resultado = new float[1];

            Location.distanceBetween(
                    anterior.lat,
                    anterior.lon,
                    atual.lat,
                    atual.lon,
                    resultado
            );

            totalMetros += resultado[0];
        }

        return totalMetros / 1000.0;
    }

    private void confirmarFinalizacaoRota() {
        android.app.AlertDialog.Builder msg =
                new android.app.AlertDialog.Builder(this);

        msg.setTitle("Confirmação");
        msg.setIcon(R.drawable.ic_question);
        msg.setMessage("Tem certeza que deseja encerrar o percurso?");

        msg.setPositiveButton("Sim, confirmo", (dialog, which) -> {

            // Executa seu método aqui
            encerrarPercurso();

        });

        msg.setNegativeButton("Não", (dialog, which) -> {
            dialog.dismiss();
        });

        msg.create().show();
    }

    private void encerrarPercurso() {

        if (rota_ativa == null) {
            showToastErro("Nenhuma rota em andamento.");
            return;
        }

        rota_ativa.sit = "FINALIZADO";
        rota_ativa.km_percorrido = kmPercorridoFinal;
        rota_ativa.bairro_final = bairroFinal;
        rota_ativa.lat_final = latitudeFinal;
        rota_ativa.lon_final = longitudeFinal;
        rota_ativa.rua_final = ruaFinal;
        rota_ativa.cidade_final = cidadeFinal;

        io.execute(() -> {

            try {

                db.rotaDAO().inserir(rota_ativa);

                runOnUiThread(() -> {

                    // PARA O GPS / FOREGROUND SERVICE
                    pararServicoRastreamento();

                    // MUITO IMPORTANTE
                    rota_ativa = null;

                    Toast.makeText(
                            getApplicationContext(),
                            "Percurso encerrado!",
                            Toast.LENGTH_LONG
                    ).show();

                    sincronizar_cadastros();
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        showToastErro(
                                "Erro ao finalizar percurso: "
                                        + e.getMessage()
                        )
                );
            }
        });
    }

    private void showToastSucess(String msge) {
        android.app.AlertDialog.Builder msg = new android.app.AlertDialog.Builder(this);
        msg.setTitle("Sucesso");
        msg.setIcon(R.drawable.ic_success);
        msg.setMessage(msge);
        msg.setPositiveButton("ok", null);
        msg.create().show();
    }

    private void showToastErro(String msge) {
        android.app.AlertDialog.Builder msg = new android.app.AlertDialog.Builder(this);
        msg.setTitle("ERRO");
        msg.setIcon(R.drawable.ic_erro);
        msg.setMessage(msge);
        msg.setPositiveButton("entendi", null);
        msg.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_option_0) {
            ver_minhas_rotas();
            return true;
        }
        if (id == R.id.menu_option_1) {

            new AlertDialog.Builder(this)
                    .setTitle("Sincronizar")
                    .setMessage("Deseja sincronizar os dados?")
                    .setPositiveButton("Sim", (d, which) -> {
                        sincronizar_cadastros();
                    })
                    .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                    .show();
            return true;
        }


        if (id == R.id.menu_option_2) {

            new AlertDialog.Builder(this)
                    .setTitle("Resincronizar")
                    .setIcon(R.drawable.ic_atencao)
                    .setMessage("Este procedimento vai preparar todos os registros para serem reenviados! Confirma?")
                    .setPositiveButton("Sim, confirmo", (d, which) -> {
                        repreparar_registros();
                    })
                    .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                    .show();


            return true;
        }
        if (id == R.id.menu_option_3) {

            // Cria o ImageView que exibirá o QR Code
            ImageView qrCode = new ImageView(this);

            qrCode.setImageResource(
                    R.drawable.qrcode_tira_entulho
            );

            // Tamanho do QR Code
            int tamanho = dpToPx(260);

            qrCode.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            tamanho,
                            tamanho
                    )
            );

            qrCode.setAdjustViewBounds(true);
            qrCode.setScaleType(
                    ImageView.ScaleType.FIT_CENTER
            );

            // Container para conseguirmos dar margem
            LinearLayout container =
                    new LinearLayout(this);

            container.setOrientation(
                    LinearLayout.VERTICAL
            );

            container.setGravity(
                    android.view.Gravity.CENTER
            );

            int margem = dpToPx(20);

            container.setPadding(
                    margem,
                    dpToPx(10),
                    margem,
                    dpToPx(10)
            );

            container.addView(qrCode);


            new AlertDialog.Builder(this)
                    .setTitle("Compartilhar App")
                    .setMessage(
                            "Aponte a câmera do outro celular para o QR Code abaixo para baixar o aplicativo."
                    )
                    .setView(container)
                    .setPositiveButton(
                            "Fechar",
                            null
                    )
                    .show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void tocarClique() {

        MediaPlayer mp = MediaPlayer.create(
                this,
                R.raw.button_click
        );

        if (mp != null) {
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        }
    }

    private void sincronizar_cadastros() {

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Enviando dados... Aguarde.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            List<RotaModel> rotas = db.rotaDAO().pegar_rotas_realizadas();
            List<PercursoModel> percurso = db.percursoDAO().pegarPercursoRotaSincronizar();

            boolean semRotas =
                    rotas == null || rotas.isEmpty();

            boolean semPercursos =
                    percurso == null || percurso.isEmpty();

            if (semRotas && semPercursos) {

                runOnUiThread(() -> {

                    progressDialog.dismiss();

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("OPS")
                            .setMessage("Não há dados para sincronizar!")
                            .setIcon(R.drawable.ic_atencao)
                            .setPositiveButton("OK", null)
                            .show();
                });

                return;
            }

            // 🔹 monta request direto (sem alterar os objetos locais)
            SincronizacaoRequest request = new SincronizacaoRequest(rotas, percurso);

            int totalRotas = rotas.size();

            service.sincronizarRotas(request).enqueue(new Callback<SincronizacaoResponse>() {
                @Override
                public void onResponse(
                        Call<SincronizacaoResponse> call,
                        Response<SincronizacaoResponse> response) {

                    runOnUiThread(() -> progressDialog.dismiss());

                    if (!response.isSuccessful()) {

                        String erroMsg = "Erro HTTP: " + response.code();

                        try {
                            if (response.errorBody() != null) {
                                erroMsg += "\n" + response.errorBody().string();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        String msgFinal = erroMsg;

                        runOnUiThread(() -> {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("ERRO")
                                    .setMessage(msgFinal)
                                    .setIcon(R.drawable.ic_erro)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });

                        return;
                    }

                    SincronizacaoResponse resposta = response.body();

                    // Não recebeu corpo
                    if (resposta == null) {

                        runOnUiThread(() -> {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("ERRO")
                                    .setMessage("O servidor respondeu, mas não confirmou a sincronização.")
                                    .setIcon(R.drawable.ic_erro)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });

                        return;
                    }

                    // servidor não informou status ok
                    if (!"ok".equalsIgnoreCase(resposta.getStatus())) {

                        runOnUiThread(() -> {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("ERRO")
                                    .setMessage("O servidor não confirmou a sincronização.")
                                    .setIcon(R.drawable.ic_erro)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });

                        return;
                    }

                    int totalRotasEnviadas =
                            rotas == null ? 0 : rotas.size();

                    int totalPercursosEnviados =
                            percurso == null ? 0 : percurso.size();

                    // CONFERE O QUE O SERVIDOR REALMENTE RECEBEU
                    if (resposta.getRotas_recebidas() != totalRotasEnviadas
                            ||
                            resposta.getPercursos_recebidos() != totalPercursosEnviados) {

                        runOnUiThread(() -> {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Sincronização incompleta")
                                    .setMessage(
                                            "Os dados enviados não foram confirmados integralmente pelo servidor."
                                                    + "\n\nRotas enviadas: "
                                                    + totalRotasEnviadas
                                                    + "\nRotas recebidas: "
                                                    + resposta.getRotas_recebidas()
                                                    + "\n\nPercursos enviados: "
                                                    + totalPercursosEnviados
                                                    + "\nPercursos recebidos: "
                                                    + resposta.getPercursos_recebidos()
                                    )
                                    .setIcon(R.drawable.ic_atencao)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });

                        return;
                    }

                    // SOMENTE AGORA altera o banco local
                    atualizarStatusLocal(rotas, percurso);

                    runOnUiThread(() -> {

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Sincronizado")
                                .setMessage(
                                        totalRotasEnviadas
                                                + " rotas e "
                                                + totalPercursosEnviados
                                                + " pontos sincronizados!"
                                )
                                .setIcon(R.drawable.ic_success)
                                .setPositiveButton("Ok, Fechar", (d, which) -> {
                                    recreate();
                                })
                                .show();
                    });
                }

                @Override
                public void onFailure(
                        Call<SincronizacaoResponse> call,
                        Throwable t) {

                    runOnUiThread(() -> {

                        progressDialog.dismiss();

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Falha na comunicação")
                                .setMessage(
                                                  "Não foi possível confirmar a sincronização.\n\n"
                                                + "Não foi possível a confirmação do servidor\n\n"
                                                + "\n\nOs dados permanecerão pendentes para nova tentativa."
                                )
                                .setIcon(R.drawable.ic_erro)
                                .setPositiveButton("OK, entendi", null)
                                .show();
                    });
                }
            });

        }).start();
    }

    private void atualizarStatusLocal(List<RotaModel> rotas, List<PercursoModel> percursos) {
        new Thread(() -> {
            rotas.forEach(r -> {
                r.sit = "SINCRONIZADO";
                r.sincronizado = 1;
            });
            percursos.forEach(p -> {
                p.sit = "SINCRONIZADO";
                p.sincronizado = 1;
            });

            db.rotaDAO().updateAll(rotas);
            db.percursoDAO().updateAll(percursos);
        }).start();
    }

    private void ver_minhas_rotas() {

        io.execute(() -> {
            try {

                List<RotaModel> minhas_rotas =
                        db.rotaDAO().pegar_rotas_realizadas();

                runOnUiThread(() -> {

                    if (minhas_rotas == null || minhas_rotas.isEmpty()) {

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Minhas rotas realizadas")
                                .setMessage("Nenhuma rota realizada encontrada.")
                                .setIcon(R.drawable.ic_percurso)
                                .setPositiveButton("Fechar", null)
                                .show();

                        return;
                    }

                    LinearLayout container = new LinearLayout(MainActivity.this);
                    container.setOrientation(LinearLayout.VERTICAL);

                    int padding = dpToPx(16);
                    container.setPadding(padding, padding, padding, padding);

                    for (int i = 0; i < minhas_rotas.size(); i++) {

                        RotaModel rota = minhas_rotas.get(i);

                        TextView txtRota = new TextView(MainActivity.this);

                        String texto =
                                "\n📅 " + DataHora.formatarData(rota.data_rota)
                                        + "\n▶ " + rota.rua_icinio
                                        + "\n🏁 " + rota.rua_final
                                        + "\n🚛 " + rota.veiculo
                                        + "\n📏 " + String.format(Locale.US, "%.2f", rota.km_percorrido) + " km"
                                        + "\n✅ " + rota.sit;

                        txtRota.setText(texto);
                        txtRota.setTextSize(17);

                        txtRota.setPadding(
                                dpToPx(8),
                                dpToPx(10),
                                dpToPx(8),
                                dpToPx(10)
                        );

                        // deixa clicável
                        txtRota.setClickable(true);

                        // efeito visual ao tocar
                        txtRota.setBackgroundResource(
                                android.R.drawable.list_selector_background
                        );

                        // abre os detalhes
                        txtRota.setOnClickListener(v -> {
                            verDetalhesRota(rota);
                        });

                        container.addView(txtRota);

                        // separador
                        if (i < minhas_rotas.size() - 1) {

                            View linha = new View(MainActivity.this);

                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            dpToPx(1)
                                    );

                            params.setMargins(
                                    dpToPx(8),
                                    dpToPx(5),
                                    dpToPx(8),
                                    dpToPx(5)
                            );

                            linha.setLayoutParams(params);
                            linha.setBackgroundColor(Color.parseColor("#777777"));

                            container.addView(linha);
                        }
                    }
                    ScrollView scrollView =
                            new ScrollView(MainActivity.this);

                    scrollView.addView(container);

                    AlertDialog dialog =
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Rotas Realizadas")
                                    .setIcon(R.drawable.ic_success)
                                    .setView(scrollView)
                                    .setPositiveButton("Fechar", null)
                                    .create();

                    dialog.setOnShowListener(d -> {

                        // limita altura do conteúdo
                        int alturaMaxima = dpToPx(420);

                        scrollView.getLayoutParams().height = alturaMaxima;
                        scrollView.requestLayout();

                    });

                    dialog.show();

                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        showToastErro(
                                "Erro ao carregar rotas: " + e.getMessage()
                        )
                );
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void verDetalhesRota(RotaModel rota) {

        DecimalFormat df = new DecimalFormat("0.00");

        String detalhes =
                "📅 Data: " + DataHora.formatarData(rota.data_rota)
                        + "\n\n✅ Situação: " + rota.sit
                        + "\n\n🗺️ Inicio: " + rota.rua_icinio
                        + "\n\n🗺️ Fim: " + rota.rua_final
                        + "\n\n🚛 Veículo: " + rota.veiculo
                        + "\n\n🛣️ Percorrido: " + df.format(rota.km_percorrido) + " Km";

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Detalhes da rota")
                .setMessage(detalhes)

                .setNeutralButton("Trajeto", (d, which) -> {
                    abrirMapaRota(rota);
                })

                .setPositiveButton("Fechar", null)
                .create();

        dialog.setOnShowListener(d -> {

            // Procura o painel onde ficam os botões
            int id = getResources().getIdentifier(
                    "buttonPanel",
                    "id",
                    "android"
            );

            View buttonPanel = dialog.findViewById(id);

            if (buttonPanel != null) {

                GradientDrawable fundo = new GradientDrawable();

                // Fundo transparente
                fundo.setColor(Color.TRANSPARENT);

                // Linha/divisor
                fundo.setStroke(1, Color.BLACK);

                buttonPanel.setBackground(fundo);
            }
        });

        dialog.show();
    }

    private void abrirMapaRota(RotaModel rota) {

        Intent intent = new Intent(
                MainActivity.this,
                MapaRotaActivity.class
        );

        intent.putExtra("uuid_rota", rota.uuid_rota);

        startActivity(intent);
    }

    private void repreparar_registros() {

        io.execute(() -> {

            try {

                List<RotaModel> rotas =
                        db.rotaDAO().pegar_rotas_realizadas();

                List<PercursoModel> percursos =
                        db.percursoDAO().pegarTodosPercurso();

                rotas.forEach(r -> {
                    r.sit = "FINALIZADO";
                    r.sincronizado = 0;
                });

                percursos.forEach(p -> {
                    p.sit = "FINALIZADO";
                    p.sincronizado = 0;
                });

                db.rotaDAO().updateAll(rotas);
                db.percursoDAO().updateAll(percursos);

                runOnUiThread(() ->
                        Toast.makeText(
                                getApplicationContext(),
                                "Todos os registros podem ser reenviados",
                                Toast.LENGTH_LONG
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        showToastErro(
                                "Erro ao repreparar registros: "
                                        + e.getMessage()
                        )
                );
            }
        });
    }

    private void pararServicoRastreamento() {

        Intent intent = new Intent(
                this,
                RastreamentoService.class
        );

        stopService(intent);

        android.util.Log.i(
                "TIRA_ENTULHO",
                "Serviço de rastreamento encerrado"
        );
    }
}