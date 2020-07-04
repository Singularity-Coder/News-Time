package com.singularitycoder.newstime.view;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.espresso.IdlingResource;

import com.singularitycoder.newstime.R;
import com.singularitycoder.newstime.adapter.NewsAdapter;
import com.singularitycoder.newstime.helpers.ApiIdlingResource;
import com.singularitycoder.newstime.helpers.HelperGeneral;
import com.singularitycoder.newstime.helpers.RequestStateMediator;
import com.singularitycoder.newstime.helpers.UiState;
import com.singularitycoder.newstime.helpers.WebViewFragment;
import com.singularitycoder.newstime.model.NewsArticle;
import com.singularitycoder.newstime.model.NewsResponse;
import com.singularitycoder.newstime.viewmodel.NewsViewModel;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.disposables.CompositeDisposable;

import static java.lang.String.valueOf;

public final class MainActivity extends AppCompatActivity {

    @Nullable
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @Nullable
    @BindView(R.id.tv_no_internet)
    TextView tvNoInternet;
    @Nullable
    @BindView(R.id.tv_nothing)
    TextView tvNothing;
    @Nullable
    @BindView(R.id.progress_circular)
    ProgressBar progressBar;
    @Nullable
    @BindView(R.id.recycler_news)
    RecyclerView recyclerView;
    @Nullable
    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    @Nullable
    @BindView(R.id.con_lay_news_home_root)
    ConstraintLayout conLayNewsHomeRoot;

    @NonNull
    private final String TAG = "MainActivity";

    @NonNull
    private final List<NewsArticle> newsList = new ArrayList<>();

    @NonNull
    private final HelperGeneral helperObject = new HelperGeneral();

    @NonNull
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Nullable
    private NewsViewModel newsViewModel;

    @NonNull
    private NewsResponse newsResponse = new NewsResponse();

    @Nullable
    private ApiIdlingResource idlingResource;

    @Nullable
    private Unbinder unbinder;

    @Nullable
    private ProgressDialog progressDialog;

    @Nullable
    private NewsAdapter newsAdapter;

    // todo unit, espresso
    // todo dagger

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        helperObject.setStatusBarColor(this, R.color.colorPrimaryDark);
        setContentView(R.layout.activity_main);
        initialise();
        setUpToolBar();
        setUpRecyclerView();
        getNewsData();
        setClickListeners();
        swipeRefreshLayout.setOnRefreshListener(this::getNewsData);
    }

    private void initialise() {
        ButterKnife.bind(this);
        unbinder = ButterKnife.bind(this);
        progressDialog = new ProgressDialog(this);
        newsViewModel = new ViewModelProvider(this).get(NewsViewModel.class);
    }

    private void setUpToolBar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("News Time");
        }
    }

    private void setUpRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        newsAdapter = new NewsAdapter(newsList, this);
        recyclerView.setAdapter(newsAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void getNewsData() {
        if (helperObject.hasInternet(this)) {
            newsViewModel.getNewsFromRepository(
                    "in",
                    "technology",
                    idlingResource
            ).observe(MainActivity.this, liveDataObserver());
        } else {
            tvNoInternet.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setRefreshing(false);
            newsList.clear();

            // If offline get List from Room DB
            newsViewModel.getAllFromRoomDbFromRepository().observe(this, liveDataObserverForRoomDb());;
        }
    }

    private void setClickListeners() {
        newsAdapter.setNewsViewListener(position -> {
            Bundle bundle = new Bundle();
            bundle.putString("SOURCE_URL", newsList.get(position).getSource().getName());
            showFragment(bundle, R.id.con_lay_news_home_root, new WebViewFragment());
        });
    }

    private void showFragment(Bundle bundle, int parentLayout, Fragment fragment) {
        fragment.setArguments(bundle);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(parentLayout, fragment)
                .addToBackStack(null)
                .commit();
    }

    private Observer<List<NewsArticle>> liveDataObserverForRoomDb() {
        Observer<List<NewsArticle>> observer = null;
        observer = (List<NewsArticle> newsArticles) -> {
            if (null != newsArticles) {
                newsViewModel.deleteAllFromRoomDbFromRepository();
                newsList.clear();
                newsList.addAll(newsArticles);
                newsAdapter.notifyDataSetChanged();
            }
        };
        return observer;
    }

    private Observer<RequestStateMediator<Object, UiState, String, String>> liveDataObserver() {
        Observer<RequestStateMediator<Object, UiState, String, String>> observer = null;
        if (helperObject.hasInternet(this)) {
            observer = requestStateMediator -> {

                if (UiState.LOADING == requestStateMediator.getStatus()) {
                    runOnUiThread(() -> {
                        progressDialog.setMessage(valueOf(requestStateMediator.getMessage()));
                        progressDialog.setCancelable(false);
                        progressDialog.setCanceledOnTouchOutside(false);
                        if (null != progressDialog && !progressDialog.isShowing())
                            progressDialog.show();
                    });
                }

                if (UiState.SUCCESS == requestStateMediator.getStatus()) {
                    runOnUiThread(() -> {
                        if (("NEWS").equals(requestStateMediator.getKey())) {
                            newsResponse = (NewsResponse) requestStateMediator.getData();
                            List<NewsArticle> newsArticles = newsResponse.getArticles();
                            newsList.addAll(newsArticles);
                            newsAdapter.notifyDataSetChanged();
                            swipeRefreshLayout.setRefreshing(false);

                            // Insert into DB
                            for (NewsArticle newsArticle : newsResponse.getArticles()) {
                                newsViewModel.insertIntoRoomDbFromRepository(newsArticle);
                            }

                            Toast.makeText(MainActivity.this, valueOf(requestStateMediator.getData()), Toast.LENGTH_SHORT).show();
                            if (null != progressDialog && progressDialog.isShowing())
                                progressDialog.dismiss();
                            tvNoInternet.setVisibility(View.GONE);
                        }
                    });
                }

                if (UiState.EMPTY == requestStateMediator.getStatus()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Something is wrong!", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                        tvNothing.setVisibility(View.VISIBLE);
                        tvNothing.setText("Nothing to show :(");
                        if (null != progressDialog && progressDialog.isShowing())
                            progressDialog.dismiss();
                        tvNoInternet.setVisibility(View.GONE);
                        Toast.makeText(this, valueOf(requestStateMediator.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }

                if (UiState.ERROR == requestStateMediator.getStatus()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        tvNothing.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                        if (null != progressDialog && progressDialog.isShowing())
                            progressDialog.dismiss();
                        tvNoInternet.setVisibility(View.GONE);
                        Toast.makeText(this, valueOf(requestStateMediator.getMessage()), Toast.LENGTH_LONG).show();
                        Log.d(TAG, "liveDataObserver: error: " + requestStateMediator.getMessage());
                    });
                }
            };
        }
        return observer;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
        compositeDisposable.dispose();
    }

    @VisibleForTesting
    @NonNull
    public IdlingResource getWaitingState() {
        if (null == idlingResource) idlingResource = new ApiIdlingResource();
        return idlingResource;
    }
}