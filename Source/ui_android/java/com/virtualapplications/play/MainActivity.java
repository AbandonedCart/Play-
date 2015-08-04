package com.virtualapplications.play;

import android.app.*;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.*;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.support.v4.widget.DrawerLayout;
import java.lang.Thread.UncaughtExceptionHandler;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.io.comparator.CompositeFileComparator;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.comparator.SizeFileComparator;
import org.apache.commons.lang3.StringUtils;
import com.android.util.FileUtils;

import com.virtualapplications.play.database.GameInfo;
import com.virtualapplications.play.database.SqliteHelper.Games;
import com.virtualapplications.play.logging.GenerateLogs;

public class MainActivity extends ActionBarActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks
{
	private static final String PREFERENCE_CURRENT_DIRECTORY = "CurrentDirectory";

	private SharedPreferences _preferences;
	private TableLayout gameListing;
	static Activity mActivity;
	private boolean isConfigured = false;
	private int numColumn = 0;
	private float localScale;
	private int currentOrientation;
	private GameInfo gameInfo;
	protected NavigationDrawerFragment mNavigationDrawerFragment;
	private UncaughtExceptionHandler mUEHandler;
	
	public static final int SORT_RECENT = 0;
	public static final int SORT_HOMEBREW = 1;
	public static final int SORT_NONE = 2;
	private int sortMethod = SORT_NONE;
	
	@Override 
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//Log.w(Constants.TAG, "MainActivity - onCreate");

		currentOrientation = getResources().getConfiguration().orientation;
//		if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
//			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//		} else {
//			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
//		}

		setContentView(R.layout.main);

		Toolbar toolbar = getSupportToolbar();
		setSupportActionBar(toolbar);
		toolbar.bringToFront();

		mNavigationDrawerFragment = (NavigationDrawerFragment)
				getFragmentManager().findFragmentById(R.id.navigation_drawer);

		// Set up the drawer.
		mNavigationDrawerFragment.setUp(
				R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));

		TypedArray a = getTheme().obtainStyledAttributes(new int[]{R.attr.colorPrimaryDark});
		int attributeResourceId = a.getColor(0, 0);
		a.recycle();
		findViewById(R.id.navigation_drawer).setBackgroundColor(Color.parseColor(
				("#" + Integer.toHexString(attributeResourceId)).replace("#ff", "#8e")
		));

		mActivity = MainActivity.this;
		_preferences = getSharedPreferences("prefs", MODE_PRIVATE);
		
		String prior_error = _preferences.getString("prior_error", null);
		if (prior_error != null) {
			displayLogOutput(prior_error);
			_preferences.edit().remove("prior_error").commit();
		} else {
			mUEHandler = new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable error) {
					if (error != null) {
						StringBuilder output = new StringBuilder();
						output.append("UncaughtException:\n");
						for (StackTraceElement trace : error.getStackTrace()) {
							output.append(trace.toString() + "\n");
						}
						String log = output.toString();
						_preferences.edit().putString("prior_error", log).commit();
						error.printStackTrace();
						android.os.Process.killProcess(android.os.Process.myPid());
						System.exit(0);
					}
				}
			};
			Thread.setDefaultUncaughtExceptionHandler(mUEHandler);
		}

		NativeInterop.setFilesDirPath(Environment.getExternalStorageDirectory().getAbsolutePath());

		EmulatorActivity.RegisterPreferences();

		if(!NativeInterop.isVirtualMachineCreated())
		{
			NativeInterop.createVirtualMachine();
		}
		
		Intent intent = getIntent();
		if (intent.getAction() != null) {
			if (intent.getAction().equals(Intent.ACTION_VIEW)) {
				launchDisk(new File(intent.getData().getPath()), true);
				getIntent().setData(null);
				setIntent(null);
			}
		}

		gameInfo = new GameInfo(MainActivity.this);
		getContentResolver().call(Games.GAMES_URI, "importDb", null, null);

		prepareFileListView();
		if (!mNavigationDrawerFragment.isDrawerOpen()) {
			if (!isConfigured) {
				getSupportActionBar().setTitle(getString(R.string.menu_title_look));
			} else {
				getSupportActionBar().setTitle(getString(R.string.menu_title_shut));
			}
		}
	}

	private static long getBuildDate(Context context)
	{
		try
		{
			ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
			ZipFile zf = new ZipFile(ai.sourceDir);
			ZipEntry ze = zf.getEntry("classes.dex");
			long time = ze.getTime();
			return time;
		}
		catch (Exception e)
		{

		}
		return 0;
	}

	private void displaySimpleMessage(String title, String message)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(title);
		builder.setMessage(message);

		builder.setPositiveButton("OK",
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int id)
					{

					}
				}
		);

		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void displaySettingsActivity()
	{
		Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
		startActivity(intent);
	}

	private void displayAboutDialog()
	{
		long buildDate = getBuildDate(this);
		String buildDateString = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(buildDate);
		String aboutMessage = String.format("Build Date: %s", buildDateString);
		displaySimpleMessage("About Play!", aboutMessage);
	}

	private String getCurrentDirectory()
	{
		if (_preferences != null) {
		return _preferences.getString(PREFERENCE_CURRENT_DIRECTORY,
			Environment.getExternalStorageDirectory().getAbsolutePath());
		} else {
			return Environment.getExternalStorageDirectory().getAbsolutePath();
		}
	}

	public static HashSet<String> getExternalMounts() {
		final HashSet<String> out = new HashSet<String>();
		String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4|fuse).*rw.*";
		String s = "";
		try {
			final java.lang.Process process = new ProcessBuilder().command("mount")
					.redirectErrorStream(true).start();
			process.waitFor();
			final InputStream is = process.getInputStream();
			final byte[] buffer = new byte[1024];
			while (is.read(buffer) != -1) {
				s = s + new String(buffer);
			}
			is.close();
		} catch (final Exception e) {

		}

		final String[] lines = s.split("\n");
		for (String line : lines) {
			if (StringUtils.containsIgnoreCase(line, "secure"))
				continue;
			if (StringUtils.containsIgnoreCase(line, "asec"))
				continue;
			if (line.matches(reg)) {
				String[] parts = line.split(" ");
				for (String part : parts) {
					if (part.startsWith("/"))
						if (!StringUtils.containsIgnoreCase(part, "vold"))
							out.add(part);
				}
			}
		}
		return out;
	}

	private void setCurrentDirectory(String currentDirectory)
	{
		SharedPreferences.Editor preferencesEditor = _preferences.edit();
		preferencesEditor.putString(PREFERENCE_CURRENT_DIRECTORY, currentDirectory);
		preferencesEditor.commit();
	}

	private void clearCurrentDirectory()
	{
		SharedPreferences.Editor preferencesEditor = _preferences.edit();
		preferencesEditor.remove(PREFERENCE_CURRENT_DIRECTORY);
		preferencesEditor.commit();
	}

	public static void resetDirectory() {
		((MainActivity) mActivity).clearCurrentDirectory();
		((MainActivity) mActivity).isConfigured = false;
		((MainActivity) mActivity).prepareFileListView();
	}

	private void clearCoverCache() {
		File dir = new File(getExternalFilesDir(null), "covers");
		for (File file : dir.listFiles()) {
			if (!file.isDirectory()) {
				file.delete();
			}
		}
	}

	public static void clearCache() {
		((MainActivity) mActivity).clearCoverCache();
	}

	private static boolean IsLoadableExecutableFileName(String fileName)
	{
		return fileName.toLowerCase().endsWith(".elf");
	}

	private static boolean IsLoadableDiskImageFileName(String fileName)
	{

		return fileName.toLowerCase().endsWith(".iso") ||
			fileName.toLowerCase().endsWith(".bin") ||
			fileName.toLowerCase().endsWith(".cso") ||
			fileName.toLowerCase().endsWith(".isz");
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		switch (position) {
			case 0:
				sortMethod = SORT_RECENT;
				prepareFileListView();
				break;
			case 1:
				sortMethod = SORT_HOMEBREW;
				prepareFileListView();
				break;
			case 2:
				sortMethod = SORT_NONE;
				prepareFileListView();
				break;
		}
	}

	@Override
	public void onNavigationDrawerBottomItemSelected(int position) {
		switch (position) {
			case 0:
				displaySettingsActivity();
				break;
			case 1:
				generateErrorLog();
				break;
			case 2:
				displayAboutDialog();
				break;
		}
	}

	public void restoreActionBar() {
		android.support.v7.app.ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setIcon(R.drawable.ic_logo);
		actionBar.setTitle(R.string.menu_title_shut);
		actionBar.setSubtitle(null);
	}
	
	private int getStatusBarHeight() {
		int result = 0;
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}
	
	private Toolbar getSupportToolbar() {
		int statusBarHeight = getStatusBarHeight();
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
		ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
		mlp.bottomMargin = - statusBarHeight;
		toolbar.setLayoutParams(mlp);

		FrameLayout content = (FrameLayout) findViewById(R.id.content_frame);
		ViewGroup.MarginLayoutParams dlp = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
		dlp.topMargin = statusBarHeight;
		content.setLayoutParams(dlp);

		return toolbar;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (!mNavigationDrawerFragment.isDrawerOpen()) {
			restoreActionBar();
			return true;
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if (NavigationDrawerFragment.mDrawerLayout != null && mNavigationDrawerFragment.isDrawerOpen()) {
			NavigationDrawerFragment.mDrawerLayout.closeDrawer(NavigationDrawerFragment.mFragmentContainerView);
		} else {
			finish();
		}
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			if (mNavigationDrawerFragment.isDrawerOpen()) {
				mNavigationDrawerFragment.mDrawerLayout.closeDrawer(NavigationDrawerFragment.mFragmentContainerView);
			} else {
				mNavigationDrawerFragment.mDrawerLayout.openDrawer(NavigationDrawerFragment.mFragmentContainerView);
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private final class ImageFinder extends AsyncTask<String, Integer, List<File>> {

		private int array;
		private ProgressDialog progDialog;

		public ImageFinder(int arrayType) {
			this.array = arrayType;
		}

		private List<File> getFileList(String path) {
			File storage = new File(path);

			String[] mediaTypes = MainActivity.this.getResources().getStringArray(array);
			FilenameFilter[] filter = new FilenameFilter[mediaTypes.length];

			int i = 0;
			for (final String type : mediaTypes) {
				filter[i] = new FilenameFilter() {

					public boolean accept(File dir, String name) {
						if (dir.getName().startsWith(".") || name.startsWith(".")) {
							return false;
						} else if (StringUtils.endsWithIgnoreCase(name, "." + type)) {
							File disk = new File(dir, name);
							String serial = gameInfo.getSerial(disk);
							return IsLoadableExecutableFileName(disk.getPath()) ||
									(serial != null && !serial.equals(""));
						} else {
							return false;
						}
					}

				};
				i++;
			}
			FileUtils fileUtils = new FileUtils();
			Collection<File> files = fileUtils.listFiles(storage, filter, -1);
			if (sortMethod == SORT_RECENT) {
				@SuppressWarnings("unchecked")
				CompositeFileComparator comparator = new CompositeFileComparator(
					SizeFileComparator.SIZE_REVERSE, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
				comparator.sort((List<File>) files);
			}
			return (List<File>) files;
		}

		private View createListItem(final File game, final int index) {

			if (!isConfigured) {

				final View childview = MainActivity.this.getLayoutInflater().inflate(
						R.layout.file_list_item, null, false);

				((TextView) childview.findViewById(R.id.game_text)).setText(game.getName());

				childview.findViewById(R.id.childview).setOnClickListener(new OnClickListener() {
					public void onClick(View view) {
						setCurrentDirectory(game.getPath().substring(0,
								game.getPath().lastIndexOf(File.separator)));
						isConfigured = true;
						prepareFileListView();
						return;
					}
				});

				return childview;
			}

			final View childview = MainActivity.this.getLayoutInflater().inflate(
					R.layout.game_list_item, null, false);

			((TextView) childview.findViewById(R.id.game_text)).setText(game.getName());

			final String[] gameStats = gameInfo.getGameInfo(game, childview);

			if (gameStats != null) {
				childview.findViewById(R.id.childview).setOnLongClickListener(
						gameInfo.configureLongClick(gameStats[1], gameStats[2], game));

				if (!gameStats[3].equals("404")) {
					gameInfo.getImage(gameStats[0], childview, gameStats[3]);
					((TextView) childview.findViewById(R.id.game_text)).setVisibility(View.GONE);
				}
			}

			childview.findViewById(R.id.childview).setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					launchDisk(game, false);
					return;
				}
			});
			return childview;
		}

		protected void onPreExecute() {
			progDialog = ProgressDialog.show(MainActivity.this,
					getString(R.string.search_games),
					getString(R.string.search_games_msg), true);
		}

		@Override
		protected List<File> doInBackground(String... paths) {

			final String root_path = paths[0];
			ArrayList<File> files = new ArrayList<File>();
			files.addAll(getFileList(root_path));

			if (!isConfigured) {
				HashSet<String> extStorage = MainActivity.getExternalMounts();
				if (extStorage != null && !extStorage.isEmpty()) {
					for (Iterator<String> sd = extStorage.iterator(); sd.hasNext();) {
						String sdCardPath = sd.next().replace("mnt/media_rw", "storage");
						if (!sdCardPath.equals(root_path)) {
							if (new File(sdCardPath).canRead()) {
								files.addAll(getFileList(sdCardPath));
							}
						}
					}
				}
			}
			
			
			return (List<File>) files;
		}

		@Override
		protected void onPostExecute(List<File> images) {
			if (progDialog != null && progDialog.isShowing()) {
				progDialog.dismiss();
			}
			if (images != null && !images.isEmpty()) {
				// Create the list of acceptable images

				Collections.sort(images);

				TableRow game_row = new TableRow(MainActivity.this);
				if (isConfigured) {
					game_row.setGravity(Gravity.CENTER);
				}
				int pad = (int) (10 * localScale + 0.5f);
				game_row.setPadding(0, 0, 0, pad);

				if (!isConfigured) {
					TableRow.LayoutParams params = new TableRow.LayoutParams(
						TableRow.LayoutParams.MATCH_PARENT,
						TableRow.LayoutParams.WRAP_CONTENT);
					params.gravity = Gravity.CENTER_VERTICAL;

					for (int i = 0; i < images.size(); i++)
					{
						game_row.addView(createListItem(images.get(i), i));
						gameListing.addView(game_row, params);
						game_row = new TableRow(MainActivity.this);
						game_row.setPadding(0, 0, 0, pad);
					}
				} else {
					TableRow.LayoutParams params = new TableRow.LayoutParams(
						TableRow.LayoutParams.WRAP_CONTENT,
						TableRow.LayoutParams.WRAP_CONTENT);
					params.gravity = Gravity.CENTER;

					int column = 0;
					for (int i = 0; i < images.size(); i++)
					{
						if (column == numColumn)
						{
							gameListing.addView(game_row, params);
							column = 0;
							game_row = new TableRow(MainActivity.this);
							game_row.setGravity(Gravity.CENTER);
							game_row.setPadding(0, 0, 0, pad);
						}
						game_row.addView(createListItem(images.get(i), i));
						column ++;
					}
					if (column != 0) {
						gameListing.addView(game_row, params);
					}
				}
				gameListing.invalidate();
			} else {
				// Display warning that no disks exist
			}
		}
	}

	public static void launchGame(File game) {
		((MainActivity) mActivity).launchDisk(game, false);
	}
	
	private void launchDisk (File game, boolean terminate) {
		try
		{
			if(IsLoadableExecutableFileName(game.getPath()))
			{
				game.setLastModified(System.currentTimeMillis());
				NativeInterop.loadElf(game.getPath());
			}
			else
			{
				game.setLastModified(System.currentTimeMillis());
				NativeInterop.bootDiskImage(game.getPath());
			}
			setCurrentDirectory(game.getPath().substring(0, game.getPath().lastIndexOf(File.separator)));
		}
		catch(Exception ex)
		{
			displaySimpleMessage("Error", ex.getMessage());
			return;
		}
		//TODO: Catch errors that might happen while loading files
		Intent intent = new Intent(getApplicationContext(), EmulatorActivity.class);
		startActivity(intent);
		if (terminate) {
			finish();
		}
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (newConfig.orientation != currentOrientation) {
			currentOrientation = newConfig.orientation;
			prepareFileListView();
		}
	}

	private boolean isAndroidTV(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			UiModeManager uiModeManager = (UiModeManager)
					context.getSystemService(Context.UI_MODE_SERVICE);
			if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
				return true;
			}
		}
		return false;
	}

	private void prepareFileListView()
	{
		if (gameInfo == null) {
			gameInfo = new GameInfo(MainActivity.this);
		}

		gameListing = (TableLayout) findViewById(R.id.game_grid);
		if (gameListing != null) {
			gameListing.removeAllViews();
		}

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		localScale = getResources().getDisplayMetrics().density;
		int screenWidth = (int) (metrics.widthPixels * localScale + 0.5f);
		int screenHeight = (int) (metrics.heightPixels * localScale + 0.5f);

		if (screenWidth > screenHeight) {
			numColumn = 3;
		} else {
			numColumn = 2;
		}

		if (isAndroidTV(getApplicationContext())) {
			numColumn += 1;
		}

		String sdcard = getCurrentDirectory();
		if (!sdcard.equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
			isConfigured = true;
		}

		if (sortMethod == SORT_HOMEBREW) {
			new ImageFinder(R.array.homebrew).execute(sdcard);
		} else {
			new ImageFinder(R.array.disks).execute(sdcard);
		}
		
	}
	
	public void generateErrorLog() {
		new GenerateLogs(MainActivity.this).execute(getFilesDir().getAbsolutePath());
	}
	
	/**
	 * Display a dialog to notify the user of prior crash
	 *
	 * @param string
	 *            A generalized summary of the crash cause
	 * @param bundle
	 *            The savedInstanceState passed from onCreate
	 */
	private void displayLogOutput(final String error) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle(R.string.report_issue);
		builder.setMessage(error);
		builder.setPositiveButton(R.string.report,
		new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				generateErrorLog();
			}
		});
		builder.setNegativeButton(R.string.dismiss,
		new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.create();
		builder.show();
	}
}
