/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).

 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 * Copyright (c) 2015 Vojtech Kral

 * LICENSE:

 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.

 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.

 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * //www.gnu.org/licenses/>.

 * @author Todo.txt contributors @yahoogroups.com>
 * *
 * @license http://www.gnu.org/licenses/gpl.html
 * *
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 * *
 * @copyright 2015 Vojtech Kral
 */
package nl.mpcjanssen.simpletask

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.IntentCompat
import android.support.v4.content.LocalBroadcastManager
import android.view.MenuItem


import nl.mpcjanssen.simpletask.util.*
import java.util.*


class Preferences : ThemedPreferenceActivity(),  SharedPreferences.OnSharedPreferenceChangeListener {

    private var prefs: SharedPreferences

    public var app: TodoApplication

    init {
        app = TodoApplication.appContext as TodoApplication
        prefs = TodoApplication.prefs
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // require restart with UI changes
        if ("theme" == key || "fontsize" == key) {
            finish();
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Constants.BROADCAST_RESTART_ACTIVITY))
        }
        if (key.equals(getString(R.string.calendar_sync_dues)) ||
                key.equals(getString(R.string.calendar_sync_thresholds))) {
            if (app.isSyncDues || app.isSyncThresholds) {
                /* Check for calendar permission */
                val permissionCheck = ContextCompat.checkSelfPermission(app,
                        Manifest.permission.WRITE_CALENDAR)

                if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this,
                            arrayOf(Manifest.permission.WRITE_CALENDAR), 0);
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Set up a listener whenever a key changes
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Set up a listener whenever a key changes
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onBuildHeaders(target: MutableList<Header> ) {
        val allHeaders = ArrayList<Header>()
        loadHeadersFromResource(R.xml.preference_headers, allHeaders);

        // Remove calendar preferences for older devices
        if (!TodoApplication.atLeastAPI(16)) {
            target.addAll(allHeaders.filter { !it.fragment.equals(CalendarPrefFragment::class.java.name) })
        } else {
            target.addAll(allHeaders)
        }
    }

    override fun isValidFragment(fragmentName: String) : Boolean {
        return true;
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
        // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                finish()
                return true
            }
            else -> {
                return false
            }
        }
    }

    abstract class PrefFragment(val xmlId: Int) : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(xmlId)
        }

    }

    class AppearancePrefFragment : PrefFragment(R.xml.appearance_preferences)
    class InterfacePrefFragment : PrefFragment(R.xml.interface_preferences)
    class WidgetPrefFragment : PrefFragment(R.xml.widget_preferences)
    class CalendarPrefFragment : PrefFragment(R.xml.calendar_preferences)



    class ConfigurationPrefFragment : PrefFragment(R.xml.configuration_preferences) {
        override  fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val rootPref = findPreference(getString(R.string.local_file_root)) as EditTextPreference
            rootPref.valueInSummary()
            rootPref.setOnPreferenceChangeListener { preference, any ->
                preference.summary = getString(R.string.local_file_root_summary)
                preference.valueInSummary(any)
                true
            }
            val appendTextPref = findPreference(getString(R.string.share_task_append_text)) as EditTextPreference
            appendTextPref.valueInSummary()
            appendTextPref.setOnPreferenceChangeListener { preference, any ->
                preference.summary = getString(R.string.share_task_append_text_summary)
                preference.valueInSummary(any)
                true
            }
        }
    }

    class DonatePrefFragment : PrefFragment(R.xml.donate_preferences) {
        var app = TodoApplication.appContext as TodoApplication
        override  fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val screen = preferenceScreen;
            val toHide: Preference
            if (app.hasDonated()) {
                toHide = screen.findPreference("donate")
            } else {
                toHide = screen.findPreference("donated")
            }
            screen.removePreference(toHide)
        }
    }

    class OtherPrefFragment : PrefFragment(R.xml.other_preferences) {
        override  fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val debugPref = findPreference("debug_info")
            debugPref.setOnPreferenceClickListener {
                startActivity(Intent(activity, DebugInfoScreen::class.java))
                true
            }
            val historyPref = findPreference("share_history")
            historyPref.setOnPreferenceClickListener {
                startActivity(Intent(activity, HistoryScreen::class.java))
                true
            }
        }
    }

    companion object {
        internal val TAG = Preferences::class.java.simpleName
    }
}

// Helper to replace %s in all setting summaries not only ListPrefences
fun Preference.valueInSummary(any: Any? = null) {
    this.summary = this.summary.replaceFirst(Regex("%s"), any?.toString() ?:  this.sharedPreferences.getString(this.key,null))
}
