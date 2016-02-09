/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.spatialite.database.spatial.activities.databasesview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ExpandableListView;

import org.json.JSONException;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import eu.geopaparazzi.library.core.ResourcesManager;
import eu.geopaparazzi.library.core.maps.SpatialiteMap;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.util.AppsUtilities;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.IActivityStarter;
import eu.geopaparazzi.library.util.StringAsyncTask;
import eu.geopaparazzi.spatialite.R;
import eu.geopaparazzi.spatialite.database.spatial.SpatialiteSourcesManager;
import eu.geopaparazzi.spatialite.database.spatial.core.enums.TableTypes;

/**
 * Activity for tile source visualisation.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class SpatialiteDatabasesTreeListActivity extends AppCompatActivity implements IActivityStarter {
    public static final int PICKFILE_REQUEST_CODE = 666;

    public static final String SHOW_TABLES = "showTables";
    public static final String SHOW_VIEWS = "showViews";

    private ExpandableListView mExpListView;
    private EditText mFilterText;
    private String mTextToFilter = "";
    private SharedPreferences mPreferences;
    private boolean[] mCheckedValues;
    private List<String> mTypeNames;
    private final LinkedHashMap<String, List<SpatialiteMap>> newMap = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.spatialitedatabases_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mFilterText = (EditText) findViewById(R.id.search_box);
        mFilterText.addTextChangedListener(filterTextWatcher);

        boolean showTables = mPreferences.getBoolean(SHOW_TABLES, true);
        boolean showViews = mPreferences.getBoolean(SHOW_VIEWS, true);

        String tableTypeName = TableTypes.SPATIALTABLE.getDescription();
        String viewTypeName = TableTypes.SPATIALVIEW.getDescription();
        mTypeNames = new ArrayList<>();
        mTypeNames.add(tableTypeName);
        mTypeNames.add(viewTypeName);
        mCheckedValues = new boolean[mTypeNames.size()];
        mCheckedValues[0] = showTables;
        mCheckedValues[1] = showViews;

        // get the listview
        mExpListView = (ExpandableListView) findViewById(R.id.expandableSourceListView);


    }

    @Override
    protected void onStart() {
        super.onStart();

        StringAsyncTask task = new StringAsyncTask(this) {
            List<SpatialiteMap> spatialiteMaps;

            protected String doBackgroundWork() {
                spatialiteMaps = SpatialiteSourcesManager.INSTANCE.getSpatialiteMaps();
                return "";
            }

            protected void doUiPostWork(String response) {
                dispose();
                try {
                    refreshData(spatialiteMaps);
                } catch (Exception e) {
                    GPLog.error(this, "Problem getting databases.", e);
                }
            }
        };
        task.setProgressDialog("", "Loading databases...", false, null);
        task.execute();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFilterText.removeTextChangedListener(filterTextWatcher);
    }

    public void add(View view) {
        try {
            String title = "Select spatialite database to add";
            String mimeType = "*/*";
            Uri uri = Uri.parse(ResourcesManager.getInstance(this).getSdcardDir().getAbsolutePath());
            AppsUtilities.pickFile(this, PICKFILE_REQUEST_CODE, title, mimeType, uri);
        } catch (Exception e) {
            GPLog.error(this, null, e);
            GPDialogs.errorDialog(this, e, null);
        }
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (PICKFILE_REQUEST_CODE): {
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        String filePath = data.getDataString();
                        final File file = new File(new URL(filePath).toURI());
                        if (file.exists()) {
                            StringAsyncTask task = new StringAsyncTask(this) {
                                public List<SpatialiteMap> spatialiteMaps;

                                protected String doBackgroundWork() {
                                    try {
                                        // add basemap to list and in mPreferences
                                        SpatialiteSourcesManager.INSTANCE.addSpatialiteMapFromFile(file);
                                        spatialiteMaps = SpatialiteSourcesManager.INSTANCE.getSpatialiteMaps();
                                    } catch (Exception e) {
                                        GPLog.error(this, "Problem getting sources.", e);
                                        return "ERROR: " + e.getLocalizedMessage();
                                    }
                                    return "";
                                }

                                protected void doUiPostWork(String response) {
                                    dispose();
                                    if (response.length() > 0) {
                                        GPDialogs.warningDialog(SpatialiteDatabasesTreeListActivity.this, response, null);
                                    } else {
                                        try {
                                            refreshData(spatialiteMaps);
                                        } catch (Exception e) {
                                            GPLog.error(this, null, e);
                                        }
                                    }
                                }
                            };
                            task.setProgressDialog("", "Adding new source...", false, null);
                            task.execute();
                        }
                    } catch (Exception e) {
                        GPDialogs.errorDialog(this, e, null);
                    }
                }
                break;
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_spatialitedatabases, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.select_type_item) {
            TableTypesChoiceDialog dialog = new TableTypesChoiceDialog();
            dialog.open(getString(R.string.select_type), SpatialiteDatabasesTreeListActivity.this, mTypeNames, mCheckedValues);
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshData(List<SpatialiteMap> spatialiteMaps) throws Exception {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(SHOW_TABLES, mCheckedValues[0]);
        editor.putBoolean(SHOW_VIEWS, mCheckedValues[1]);
        editor.apply();

        boolean log = GPLog.LOG;
        if (log) {
            GPLog.addLogEntry(this, "Available baseMaps:");
            for (SpatialiteMap tmpSpatialiteMap : spatialiteMaps) {
                GPLog.addLogEntry(this, tmpSpatialiteMap.toString());
            }
        }

        newMap.clear();
        for (SpatialiteMap spatialiteMap : spatialiteMaps) {
            String key = spatialiteMap.databasePath;
            List<SpatialiteMap> newValues = newMap.get(key);
            if (newValues == null) {
                newValues = new ArrayList<>();
                newMap.put(key, newValues);
            }

            boolean doAdd = false;
            String tableType = spatialiteMap.tableType;
            if (tableType == null) {
                doAdd = true;
            } else if (mCheckedValues[0] && tableType.equals(TableTypes.SPATIALTABLE.getDescription())) {
                doAdd = true;
            } else if (mCheckedValues[1] && tableType.equals(TableTypes.SPATIALVIEW.getDescription())) {
                doAdd = true;
            }
            if (log) {
                GPLog.addLogEntry(this, "doAdd: " + doAdd + " baseMap: " + spatialiteMap);
            }

            if (mTextToFilter.length() > 0) {
                // filter text
                String filterString = mTextToFilter.toLowerCase();
                String valueString = spatialiteMap.databasePath.toLowerCase();
                if (!valueString.contains(filterString)) {
                    valueString = spatialiteMap.title.toLowerCase();
                    if (!valueString.contains(filterString)) {
                        doAdd = false;
                    }
                }
            }
            if (doAdd) {
                newValues.add(spatialiteMap);
                if (log) {
                    GPLog.addLogEntry(this, "Added: " + spatialiteMap.toString());
                }
            }
            if (newValues.size() == 0) {
                newMap.remove(key);
            }

        }

        SpatialiteDatabasesExpandableListAdapter listAdapter = new SpatialiteDatabasesExpandableListAdapter(this, newMap);
        mExpListView.setAdapter(listAdapter);
        mExpListView.setClickable(true);
        mExpListView.setFocusable(true);
        mExpListView.setFocusableInTouchMode(true);
        mExpListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
//                int index = 0;
//                SpatialiteMap selectedSpatialiteMap = null;
//                for (Entry<String, List<SpatialiteMap>> entry : newMap.entrySet()) {
//                    if (groupPosition == index) {
//                        List<SpatialiteMap> value = entry.getValue();
//                        selectedSpatialiteMap = value.get(childPosition);
//                        break;
//                    }
//                    index++;
//                }
//                try {
//                    SpatialiteSourcesManager.INSTANCE.setSelectedBaseMap(selectedSpatialiteMap);
//                } catch (jsqlite.Exception e) {
//                    GPLog.error(SourcesTreeListActivity.this, "ERROR", e);
//                }
//                finish();
                return false;
            }
        });

        mExpListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // When clicked on child, function longClick is executed
                if (ExpandableListView.getPackedPositionType(id) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                    int groupPosition = ExpandableListView.getPackedPositionGroup(id);
                    int childPosition = ExpandableListView.getPackedPositionChild(id);

                    int index = 0;
                    for (String group : newMap.keySet()) {
                        if (index == groupPosition) {
                            List<SpatialiteMap> spatialiteMapList = newMap.get(group);
                            final SpatialiteMap spatialiteMap = spatialiteMapList.get(childPosition);

                            GPDialogs.yesNoMessageDialog(SpatialiteDatabasesTreeListActivity.this, String.format(getString(R.string.remove_from_list), spatialiteMap.title), new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        SpatialiteSourcesManager.INSTANCE.removeSpatialiteMap(spatialiteMap);
                                    } catch (JSONException e) {
                                        GPLog.error(this, null, e);
                                    }

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                refreshData(SpatialiteSourcesManager.INSTANCE.getSpatialiteMaps());
                                            } catch (Exception e) {
                                                GPLog.error(this, null, e);
                                            }
                                        }
                                    });

                                }
                            }, null);

                            return true;
                        }
                        index++;
                    }
                    return true;
                }
                return false;
            }
        });

        int groupCount = listAdapter.getGroupCount();
        for (int i = 0; i < groupCount; i++) {
            mExpListView.expandGroup(i);
        }
    }


    private TextWatcher filterTextWatcher = new TextWatcher() {

        public void afterTextChanged(Editable s) {
            // ignore
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // ignore
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mTextToFilter = s.toString();
            try {
                refreshData(SpatialiteSourcesManager.INSTANCE.getSpatialiteMaps());
            } catch (Exception e) {
                GPLog.error(SpatialiteDatabasesTreeListActivity.this, "ERROR", e);
            }
        }
    };

    @Override
    public Context getContext() {
        return this;
    }
}