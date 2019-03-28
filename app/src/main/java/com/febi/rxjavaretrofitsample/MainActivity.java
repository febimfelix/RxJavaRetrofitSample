package com.febi.rxjavaretrofitsample;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.febi.rxjavaretrofitsample.datastructure.GithubIssue;
import com.febi.rxjavaretrofitsample.datastructure.GithubRepo;
import com.febi.rxjavaretrofitsample.datastructure.GithubRepoDeserializer;
import com.febi.rxjavaretrofitsample.helpers.CredentialsDialog;
import com.febi.rxjavaretrofitsample.helpers.GithubApi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements CredentialsDialog.ICredentialsDialogListener {

    GithubApi githubApi;
    String userName = "";
    String password = "";

    Spinner repositoriesSpinner;
    Spinner issuesSpinner;
    EditText commentEditText;
    Button sendButton;
    Button loadReposButton;
    ProgressBar progressBar;

    private SharedPreferences sharedPreferences;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar     = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);

        sharedPreferences   = getSharedPreferences("my_app", MODE_PRIVATE);

        sendButton          = findViewById(R.id.send_comment_button);
        progressBar         = findViewById(R.id.id_main_progress_bar);

        repositoriesSpinner = findViewById(R.id.repositories_spinner);
        repositoriesSpinner.setEnabled(false);
        repositoriesSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"No respositories available"}));
        repositoriesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(parent.getSelectedItem() instanceof GithubRepo) {
                    progressBar.setVisibility(View.VISIBLE);
                    GithubRepo githubRepo   = (GithubRepo) parent.getSelectedItem();
                    compositeDisposable.add(githubApi.getIssues(githubRepo.owner, githubRepo.name)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(getIssuesObserver()));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        issuesSpinner       = findViewById(R.id.issues_spinner);
        issuesSpinner.setEnabled(false);
        issuesSpinner.setAdapter(new ArrayAdapter<>(MainActivity.this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Please select repository"}));

        commentEditText     = findViewById(R.id.comment_edittext);
        loadReposButton     = findViewById(R.id.loadRepos_button);

        checkForLogin();
        createGithubAPI();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_credentials:
                showCredentialsDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForLogin() {
        this.userName   = sharedPreferences.getString("username", "");
        this.password   = sharedPreferences.getString("password", "");
        if(!this.userName.isEmpty() && !this.password.isEmpty())
            loadReposButton.setEnabled(true);
    }

    private void showCredentialsDialog() {
        CredentialsDialog dialog    = new CredentialsDialog();
        Bundle arguments            = new Bundle();
        arguments.putString("username", userName);
        arguments.putString("password", password);
        dialog.setArguments(arguments);

        dialog.show(getSupportFragmentManager(), "credentialsDialog");
    }

    @Override
    public void onDialogPositiveClick(String username, String password) {
        this.userName   = username;
        this.password   = password;
        sharedPreferences.edit().putString("username", username).apply();
        sharedPreferences.edit().putString("password", password).apply();
        loadReposButton.setEnabled(true);
    }

    private void createGithubAPI() {
        Gson gson                   = new GsonBuilder()
                                        .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                                        .registerTypeAdapter(GithubRepo.class, new GithubRepoDeserializer())
                                        .create();

        OkHttpClient okHttpClient   = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request originalRequest     = chain.request();

                        Request.Builder builder     = originalRequest.newBuilder().header("Authorization",
                                Credentials.basic(userName, password));

                        Request newRequest          = builder.build();
                        return chain.proceed(newRequest);
                    }
                }).build();

        Retrofit retrofit           = new Retrofit.Builder()
                .baseUrl(GithubApi.API_ENDPOINT)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        githubApi                   = retrofit.create(GithubApi.class);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.loadRepos_button:
                progressBar.setVisibility(View.VISIBLE);
                compositeDisposable.add(githubApi.getRepos()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(getRepositoriesObserver()));
                break;
            case R.id.send_comment_button:
                String newComment                   = commentEditText.getText().toString();
                if (!newComment.isEmpty()) {
                    progressBar.setVisibility(View.VISIBLE);
                    GithubIssue selectedItem        = (GithubIssue) issuesSpinner.getSelectedItem();
                    selectedItem.comment            = newComment;
                    compositeDisposable.add(githubApi.postComment(selectedItem.comments_url, selectedItem)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeWith(getCommentObserver()));
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a comment", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    DisposableSingleObserver<List<GithubIssue>> getIssuesObserver() {
        return new DisposableSingleObserver<List<GithubIssue>>() {
            @Override
            public void onSuccess(List<GithubIssue> value) {
                progressBar.setVisibility(View.GONE);
                if (!value.isEmpty()) {
                    ArrayAdapter<GithubIssue> spinnerAdapter    = new ArrayAdapter<>(
                            MainActivity.this, android.R.layout.simple_spinner_dropdown_item, value);
                    issuesSpinner.setEnabled(true);
                    commentEditText.setEnabled(true);
                    sendButton.setEnabled(true);
                    issuesSpinner.setAdapter(spinnerAdapter);
                } else {
                    ArrayAdapter<String> spinnerAdapter         = new ArrayAdapter<>(
                            MainActivity.this, android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"Repository has no issues"});
                    issuesSpinner.setEnabled(false);
                    commentEditText.setEnabled(false);
                    sendButton.setEnabled(false);
                    issuesSpinner.setAdapter(spinnerAdapter);
                }
            }

            @Override
            public void onError(Throwable e) {
                progressBar.setVisibility(View.GONE);
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Cannot load issues", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private DisposableSingleObserver<List<GithubRepo>> getRepositoriesObserver() {
        return new DisposableSingleObserver<List<GithubRepo>>() {
            @Override
            public void onSuccess(List<GithubRepo> value) {
                progressBar.setVisibility(View.GONE);
                if (!value.isEmpty()) {
                    ArrayAdapter<GithubRepo> spinnerAdapter     = new ArrayAdapter<>(
                            MainActivity.this, android.R.layout.simple_spinner_dropdown_item, value);
                    repositoriesSpinner.setAdapter(spinnerAdapter);
                    repositoriesSpinner.setEnabled(true);
                } else {
                    ArrayAdapter<String> spinnerAdapter         = new ArrayAdapter<>(
                            MainActivity.this, android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"User has no repositories"});
                    repositoriesSpinner.setAdapter(spinnerAdapter);
                    repositoriesSpinner.setEnabled(false);
                }
            }

            @Override
            public void onError(Throwable e) {
                progressBar.setVisibility(View.GONE);
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Can not load repositories", Toast.LENGTH_SHORT).show();

            }
        };
    }

    private DisposableSingleObserver<ResponseBody> getCommentObserver() {
        return new DisposableSingleObserver<ResponseBody>() {
            @Override
            public void onSuccess(ResponseBody value) {
                progressBar.setVisibility(View.GONE);
                commentEditText.setText("");
                Toast.makeText(MainActivity.this, "Comment created", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Throwable e) {
                progressBar.setVisibility(View.GONE);
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Can not create comment", Toast.LENGTH_SHORT).show();
            }
        };
    }
}
