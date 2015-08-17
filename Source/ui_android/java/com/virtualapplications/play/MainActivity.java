package com.virtualapplications.play;

import android.app.*;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.*;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.GridView;
import android.widget.TextView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import java.lang.Thread.UncaughtExceptionHandler;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.io.comparator.CompositeFileComparator;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.comparator.SizeFileComparator;
import org.apache.commons.lang3.StringUtils;
import com.android.util.FileUtils;
import android.graphics.Point;

import com.alexvasilkov.foldablelayout.UnfoldableView;

import com.virtualapplications.play.database.GameInfo;
import com.virtualapplications.play.database.SqliteHelper.Games;
import com.virtualapplications.play.logging.GenerateLogs;

public class MainActivity extends ActionBarActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks
{
	private static final String PREFERENCE_CURRENT_DIRECTORY = "CurrentDirectory";

	private SharedPreferences _preferences;
	static Activity mActivity;
	private boolean isConfigured = false;
	private int currentOrientation;
	private GameInfo gameInfo;
	protected NavigationDrawerFragment mNavigationDrawerFragment;
	private UncaughtExceptionHandler mUEHandler;
	
	private List<File> currentGames = new ArrayList<File>();
	
	public static final int SORT_RECENT = 0;
	public static final int SORT_HOMEBREW = 1;
	public static final int SORT_NONE = 2;
	private int sortMethod = SORT_NONE;

    private UnfoldableView mUnfoldableView;
    private View mListTouchInterceptor;
    private FrameLayout mDetailsLayout;

	//To prevent multiple padding buildups on multiple rotations.
	private int relative_layout_original_right_padding;
	private int navigation_drawer_original_bottom_padding;

	@Override 
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//Log.w(Constants.TAG, "MainActivity - onCreate");

		_preferences = getSharedPreferences("prefs", MODE_PRIVATE);
		currentOrientation = getResources().getConfiguration().orientation;

		SettingsActivity.ChangeTheme(null, this);
		if (isAndroidTV(this)) {
			setContentView(R.layout.tele);
		} else {
			setContentView(R.layout.main);
		}
		mActivity = MainActivity.this;
		
		String prior_error = _preferences.getString("prior_error", null);
		if (prior_error != null) {
			displayLogOutput(prior_error);
			_preferences.edit().remove("prior_error").commit();
		} else {
			mUEHandler = new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable error) {
					if (error != null) {
						error.printStackTrace();
						StringBuilder output = new StringBuilder();
						output.append("UncaughtException:\n");
						for (StackTraceElement trace : error.getStackTrace()) {
							output.append(trace.toString() + "\n");
						}
						String log = output.toString();
						_preferences.edit().putString("prior_error", log).commit();
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
        
		Toolbar toolbar = getSupportToolbar();
		setSupportActionBar(toolbar);
		toolbar.bringToFront();

		if (isAndroidTV(this)) {
			configureActionBar();
			invalidateOptionsMenu();

			ListView top_navigation = (ListView) findViewById(R.id.nav_listview);
			top_navigation.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, new String[]{
				getString(R.string.file_list_recent),
				getString(R.string.file_list_homebrew),
				getString(R.string.file_list_default)
			}));
			top_navigation.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					selectionTop(position);
				}
			});
			ListView bottom_navigation = (ListView) findViewById(R.id.nav_listview_bottom);
			bottom_navigation.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, new String[]{
				getString(R.string.main_menu_settings),
				getString(R.string.main_menu_debug),
				getString(R.string.main_menu_about)
			}));
			bottom_navigation.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					selectionBottom(position);
				}
			});
		} else {
			mNavigationDrawerFragment = (NavigationDrawerFragment)
					getFragmentManager().findFragmentById(R.id.navigation_drawer);

			// Set up the drawer.
			mNavigationDrawerFragment.setUp(
					R.id.navigation_drawer,
					(DrawerLayout) findViewById(R.id.drawer_layout));
		}

		gameInfo = new GameInfo(MainActivity.this);
		getContentResolver().call(Games.GAMES_URI, "importDb", null, null);

		prepareFileListView(false);
		if (!isAndroidTV(this) && !mNavigationDrawerFragment.isDrawerOpen()) {
			if (!isConfigured) {
				getSupportActionBar().setTitle(getString(R.string.menu_title_look));
			} else {
				getSupportActionBar().setTitle(getString(R.string.menu_title_shut));
			}
		}
	}
    
	private ActionBar configureActionBar() {
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setIcon(R.drawable.ic_logo);
		if (!isConfigured) {
			actionBar.setTitle(getString(R.string.menu_title_look));
		} else {
			actionBar.setTitle(getString(R.string.menu_title_shut));
		}
		actionBar.setSubtitle(null);
		return actionBar;
	}

	private Toolbar getSupportToolbar() {
		//this sets toolbar margin, but in effect moving the DrawerLayout
		int statusBarHeight = getStatusBarHeight();

		View toolbar = findViewById(R.id.my_awesome_toolbar);
		final ViewGroup content = (ViewGroup) findViewById(R.id.content_frame);
		
		ViewGroup.MarginLayoutParams dlp = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
		dlp.topMargin = statusBarHeight;
		content.setLayoutParams(dlp);

		generateGradient(content);

		ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
		mlp.bottomMargin = - statusBarHeight;
		toolbar.setLayoutParams(mlp);
		View navigation_drawer = findViewById(R.id.navigation_drawer);
		ViewGroup.MarginLayoutParams mlp2 = (ViewGroup.MarginLayoutParams) navigation_drawer.getLayoutParams();
		mlp2.topMargin = statusBarHeight;
		navigation_drawer.setLayoutParams(mlp2);

		Point p = getNavigationBarSize(this);
		/*
		This will take account of nav bar to right/bottom
		Not sure if there is a way to detect left/top? thus always pad right/bottom for now
		*/
		View relative_layout = findViewById(R.id.relative_layout);
		if (p.x != 0){
			if (relative_layout_original_right_padding == 0){
				relative_layout_original_right_padding = relative_layout.getPaddingRight();
			}
			relative_layout.setPadding(
				relative_layout.getPaddingLeft(),
				relative_layout.getPaddingTop(),
				relative_layout_original_right_padding + p.x,
				relative_layout.getPaddingBottom());

			navigation_drawer.setPadding(
				navigation_drawer.getPaddingLeft(),
				navigation_drawer.getPaddingTop(),
				navigation_drawer.getPaddingRight(),
				navigation_drawer_original_bottom_padding);
		} else if (p.y != 0){
			navigation_drawer.invalidate();
			if (navigation_drawer_original_bottom_padding == 0){
				navigation_drawer_original_bottom_padding = navigation_drawer.getPaddingRight();
			}
			navigation_drawer.setPadding(
				navigation_drawer.getPaddingLeft(), 
				navigation_drawer.getPaddingTop(), 
				navigation_drawer.getPaddingRight(),
				navigation_drawer_original_bottom_padding + p.y);

			relative_layout.setPadding(
				relative_layout.getPaddingLeft(),
				relative_layout.getPaddingTop(),
				relative_layout_original_right_padding,
				relative_layout.getPaddingBottom());
		}
		return (Toolbar) toolbar;
	}

	private void generateGradient(ViewGroup content) {
		if (content != null) {
			int[] colors = new int[2];// you can increase array size to add more colors to gradient.
			TypedArray a = getTheme().obtainStyledAttributes(new int[]{R.attr.colorGradientStart});
			int attributeResourceIdStart = a.getColor(0, 0);
			a.recycle();
//			float[] hsv = new float[3];
//			Color.colorToHSV(attributeResourceIdStart, hsv);
//			hsv[2] *= 1.0f;// make it darker
//			colors[0] = Color.HSVToColor(hsv);
			colors[0] = Color.parseColor("#" + Integer.toHexString(attributeResourceIdStart));
			TypedArray b = getTheme().obtainStyledAttributes(new int[]{R.attr.colorGradientEnd});
			int attributeResourceIdEnd = b.getColor(0, 0);
			b.recycle();
//			colors[1] = Color.rgb(20,20,20);
			colors[1] = Color.parseColor("#" + Integer.toHexString(attributeResourceIdEnd));
			GradientDrawable gradientbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
			content.setBackground(gradientbg);
		}
		TypedArray a = getTheme().obtainStyledAttributes(new int[]{R.attr.colorPrimaryDark});
		int attributeResourceId = a.getColor(0, 0);
		a.recycle();
		findViewById(R.id.navigation_drawer).setBackgroundColor(Color.parseColor(
				("#" + Integer.toHexString(attributeResourceId)).replace("#ff", "#8e")
		));
	}

	public static Point getNavigationBarSize(Context context) {
		Point appUsableSize = getAppUsableScreenSize(context);
		Point realScreenSize = getRealScreenSize(context);
		return new Point(realScreenSize.x - appUsableSize.x, realScreenSize.y - appUsableSize.y);
	}

	public static Point getAppUsableScreenSize(Context context) {
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = windowManager.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size;
	}

	public static Point getRealScreenSize(Context context) {
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = windowManager.getDefaultDisplay();
		Point size = new Point();

		if (Build.VERSION.SDK_INT >= 17) {
            display.getRealSize(size);
		} else if (Build.VERSION.SDK_INT >= 14) {
            try {
                size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            }
            catch (IllegalAccessException e) {}
            catch (InvocationTargetException e) {}
            catch (NoSuchMethodException e) {}
		}

		return size;
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
		startActivityForResult(intent, 0);

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0) {
			SettingsActivity.ChangeTheme(null, this);
			generateGradient((FrameLayout) findViewById(R.id.content_frame));
		}
	}


	private void displayAboutDialog()
	{
		long buildDate = getBuildDate(this);
		String buildDateString = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(buildDate);
		String aboutMessage = String.format("Build Date: %s", buildDateString);
		displaySimpleMessage("About Play!", aboutMessage);
	}

	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (!isAndroidTV(this)) {
			mNavigationDrawerFragment.onConfigurationChanged(newConfig);
		}
		if (newConfig.orientation != currentOrientation) {
			currentOrientation = newConfig.orientation;
			if (!isAndroidTV(this)) {
				getSupportToolbar();
			}
			if (currentGames != null && !currentGames.isEmpty()) {
				prepareFileListView(true);
			} else {
				prepareFileListView(false);
			}
		}
		
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
		((MainActivity) mActivity).prepareFileListView(false);
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
    
    private void selectionTop(int position) {
        switch (position) {
            case 0:
                sortMethod = SORT_RECENT;
                prepareFileListView(false);
                break;
            case 1:
                sortMethod = SORT_HOMEBREW;
                prepareFileListView(false);
                break;
            case 2:
                sortMethod = SORT_NONE;
                prepareFileListView(false);
                break;
        }
    }

	@Override
	public void onNavigationDrawerItemSelected(int position) {
        selectionTop(position);
	}
    
    private void selectionBottom(int position) {
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

	@Override
	public void onNavigationDrawerBottomItemSelected(int position) {
        selectionBottom(position);
	}

	public void restoreActionBar() {
		ActionBar actionBar = getSupportActionBar();
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (isAndroidTV(this) || !mNavigationDrawerFragment.isDrawerOpen()) {
			restoreActionBar();
			return true;
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onBackPressed() {
        if (mUnfoldableView != null && (mUnfoldableView.isUnfolded() || mUnfoldableView.isUnfolding())) {
            mUnfoldableView.foldBack();
        } else if (NavigationDrawerFragment.mDrawerLayout != null && mNavigationDrawerFragment.isDrawerOpen()) {
			NavigationDrawerFragment.mDrawerLayout.closeDrawer(NavigationDrawerFragment.mFragmentContainerView);
		} else {
			finish();
		}
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!isAndroidTV(this)) {
                if (mNavigationDrawerFragment.isDrawerOpen()) {
                    mNavigationDrawerFragment.mDrawerLayout.closeDrawer(NavigationDrawerFragment.mFragmentContainerView);
                } else {
                    mNavigationDrawerFragment.mDrawerLayout.openDrawer(NavigationDrawerFragment.mFragmentContainerView);
                }
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
                        if (dir.getName().equals("obb") || dir.getName().equals("cache")
                            || dir.getName().startsWith(".") || name.startsWith(".")) {
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
			return (List<File>) files;
		}

		protected void onPreExecute() {
			progDialog = ProgressDialog.show(MainActivity.this,
                    getString(isConfigured ? R.string.updating_db : R.string.search_games),
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
				currentGames = images;
				// Create the list of acceptable images
				populateImages(images);
			} else {
				// Display warning that no disks exist
			}
		}
	}
	
	private View createListItem(final File game, final View childview) {
		if (!isConfigured) {
			
			((TextView) childview.findViewById(R.id.game_text)).setText(game.getName());
			
			childview.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					setCurrentDirectory(game.getPath().substring(0,
						game.getPath().lastIndexOf(File.separator)));
					isConfigured = true;
					prepareFileListView(false);
					return;
				}
			});
			
			return childview;

		} else {
		
			((TextView) childview.findViewById(R.id.game_text)).setText(game.getName());
			
			final String[] gameStats = gameInfo.getGameInfo(game, childview);
			
			if (gameStats != null) {
                Bitmap cover = null;
				if (!gameStats[3].equals("404")) {
					cover = gameInfo.getImage(gameStats[0], childview, gameStats[3]);
					((TextView) childview.findViewById(R.id.game_text)).setVisibility(View.GONE);
				}
				childview.setOnClickListener(
					configureOnClick(childview, cover, gameStats[1], gameStats[2], game)
				);
            } else {
				childview.setOnClickListener(
					configureOnClick(childview, null, game.getName(), null, game)
				);
            }

			return childview;
		}
	}

    public OnClickListener configureOnClick(final View childview, final Bitmap cover, final String title, final String overview, final File game) {
        return new OnClickListener() {
            public void onClick(View view) {
                if (cover != null) {
                    BitmapDrawable backgroundDrawable = new BitmapDrawable(cover);
                    mDetailsLayout.setBackgroundDrawable(backgroundDrawable);
                }
                if (overview != null) {
                    TextView detailView = (TextView) mDetailsLayout.findViewById(R.id.game_details);
                    detailView.setText(overview);
                }
                Button launch = (Button) mDetailsLayout.findViewById(R.id.game_launch);
                if (title != null) {
                    launch.setText(getString(R.string.launch_game, title));
                } else {
                    launch.setText(getString(R.string.launch_game, game.getName()));
                }
                launch.setOnClickListener(new OnClickListener() {
                    public void onClick(View view) {
                        mUnfoldableView.foldBack();
                        launchDisk(game);
                    }
                });
                mUnfoldableView.unfold(childview.findViewById(R.id.game_icon), mDetailsLayout);
            }
        };
    }
	
	private void populateImages(List<File> images) {
		if (sortMethod == SORT_RECENT) {
			@SuppressWarnings("unchecked")
			CompositeFileComparator comparator = new CompositeFileComparator(
				SizeFileComparator.SIZE_REVERSE, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
			comparator.sort(images);
		} else {
			Collections.sort(images);
		}
		GridView gameGrid = (GridView) findViewById(R.id.game_grid);
		if (gameGrid != null && gameGrid.isShown()) {
			gameGrid.setAdapter(null);
		}

		GamesAdapter adapter = new GamesAdapter(MainActivity.this, isConfigured ? R.layout.game_list_item : R.layout.file_list_item, images);

		if (isConfigured){
            gameGrid.setDrawSelectorOnTop(false);
            gameGrid.setNumColumns(GridView.AUTO_FIT);
			gameGrid.setColumnWidth((int) getResources().getDimension(R.dimen.cover_width));
        } else {
            gameGrid.setDrawSelectorOnTop(true);
            gameGrid.setSelector(getResources().getDrawable(R.drawable.game_selector));
            if (isAndroidTV(this)) {
                gameGrid.setNumColumns(2);
            } else {
                gameGrid.setNumColumns(1);
            }
        }
		gameGrid.setAdapter(adapter);
		gameGrid.invalidate();
        
        mListTouchInterceptor = (View) findViewById(R.id.touch_interceptor_view);
        mListTouchInterceptor.setClickable(false);
        
        mDetailsLayout = (FrameLayout) findViewById(R.id.details_layout);
        mDetailsLayout.setVisibility(View.INVISIBLE);
        
        mUnfoldableView = (UnfoldableView) findViewById(R.id.unfoldable_view);
        mUnfoldableView.setGesturesEnabled(false);
        
        mUnfoldableView.setOnFoldingListener(new UnfoldableView.SimpleFoldingListener() {
            @Override
            public void onUnfolding(UnfoldableView unfoldableView) {
                mListTouchInterceptor.setClickable(true);
                mDetailsLayout.setVisibility(View.VISIBLE);
            }
            
            @Override
            public void onUnfolded(UnfoldableView unfoldableView) {
                mListTouchInterceptor.setClickable(false);
            }
            
            @Override
            public void onFoldingBack(UnfoldableView unfoldableView) {
                mListTouchInterceptor.setClickable(true);
            }
            
            @Override
            public void onFoldedBack(UnfoldableView unfoldableView) {
                mListTouchInterceptor.setClickable(false);
                mDetailsLayout.setVisibility(View.INVISIBLE);
            }
        });

	}
	
	public class GamesAdapter extends ArrayAdapter<File> {

		private final int layoutid;
		private final int padding;
		private List<File> games;
        private int original_bottom_pad;
		
		public GamesAdapter(Context context, int ResourceId, List<File> images) {
			super(context, ResourceId, images);
			this.games = images;
			this.layoutid = ResourceId;
			this.padding = getNavigationBarSize(context).y;
		}

		public int getCount() {
			return games.size();
		}

		public File getItem(int position) {
			return games.get(position);
		}

		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(layoutid, null);
			}
			final File game = games.get(position);
			if (game != null) {
				createListItem(game, v);
			}
			if (original_bottom_pad == 0){
				original_bottom_pad = v.getPaddingBottom();
			}
			if (position == games.size() - 1) {
				v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    original_bottom_pad + padding);
            } else {
                v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    original_bottom_pad);
            }
            // Hack to fix the GridView height without extending into custom class
            // http://stackoverflow.com/questions/8481844/gridview-height-gets-cut
			return v;
		}
	}

	public static void launchGame(File game) {
		((MainActivity) mActivity).launchDisk(game);
	}
	
	private void launchDisk (File game) {
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
		}
		catch(Exception ex)
		{
			Toast.makeText(getApplicationContext(), "Error: " + ex.getMessage(),
						   Toast.LENGTH_SHORT).show();
			ex.printStackTrace();
			return;
		}
		//TODO: Catch errors that might happen while loading files
		Intent intent = new Intent(getApplicationContext(), EmulatorActivity.class);
		startActivity(intent);
	}
	
	public boolean isAndroidTV(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			UiModeManager uiModeManager = (UiModeManager)
					context.getSystemService(Context.UI_MODE_SERVICE);
			if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
				return true;
			}
		}
		return false;
	}

	private void prepareFileListView(boolean retainList)
	{
		if (gameInfo == null) {
			gameInfo = new GameInfo(MainActivity.this);
		}

		String sdcard = getCurrentDirectory();
		if (!sdcard.equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
			isConfigured = true;
		}

		if (isConfigured && retainList) {
			populateImages(currentGames);
		} else {
			if (sortMethod == SORT_HOMEBREW) {
				new ImageFinder(R.array.homebrew).execute(sdcard);
			} else {
				new ImageFinder(R.array.disks).execute(sdcard);
			}
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
