package org.witness.informacam.app;

import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.odkparser.widgets.ODKSeekBar.OnMediaRecorderStopListener;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.witness.informacam.InformaCam;
import org.witness.informacam.app.screens.CameraFragment;
import org.witness.informacam.app.screens.GalleryFragment;
import org.witness.informacam.app.screens.HomeFragment;
import org.witness.informacam.app.screens.UserManagementFragment;
import org.witness.informacam.app.screens.menus.MediaActionMenu;
import org.witness.informacam.app.screens.popups.PopupClickListener;
import org.witness.informacam.app.screens.popups.TextareaPopup;
import org.witness.informacam.app.utils.Constants;
import org.witness.informacam.app.utils.Constants.App.Home;
import org.witness.informacam.app.utils.Constants.Codes;
import org.witness.informacam.app.utils.Constants.Codes.Routes;
import org.witness.informacam.app.utils.Constants.HomeActivityListener;
import org.witness.informacam.app.utils.Constants.Preferences;
import org.witness.informacam.app.utils.actions.ContextMenuAction;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.ui.CameraActivity;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.ListAdapterListener;
import org.witness.informacam.utils.Constants.Models;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.Toast;

@SuppressLint("HandlerLeak")
public class HomeActivity extends FragmentActivity implements HomeActivityListener, InformaCamEventListener,
		ListAdapterListener, OnMediaRecorderStopListener, ICacheWordSubscriber
{
	Intent init, route;

	private final static String LOG = Constants.App.Home.LOG;

	private static final int USE_USER_MANAGEMENT_FRAGMENT = 0;
	
	private static final int INDEX_MAIN = 0;
	private static final int INDEX_USER_MANAGEMENT = 1;
	private static final int INDEX_GALLERY = 1 + USE_USER_MANAGEMENT_FRAGMENT;
	private static final int INDEX_CAMERA = 2 + USE_USER_MANAGEMENT_FRAGMENT;
	
	private String lastLocale = null;

	List<Fragment> fragments = new Vector<Fragment>();
	HomeFragment mainFragment;
	GalleryFragment galleryFragment;
	Fragment userManagementFragment, cameraFragment;

	boolean initGallery = false;

	int visibility = View.VISIBLE;

	ViewPager viewPager;
	TabPager pager;

	InformaCam informaCam;

	MediaActionMenu mam;
	// WaitPopup waiter;

	Intent toEditor, toCamera;
	
	CacheWordHandler cacheWord;

	private boolean mFirstTime = false;
	
	@SuppressWarnings("unused")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		cacheWord = new CacheWordHandler(this, this);
		cacheWord.connectToService();
		
		informaCam = (InformaCam)getApplication();		
		informaCam.setEventListener(this);
		
		if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefBlockScreenshots", false))
		{
	  		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
	  				WindowManager.LayoutParams.FLAG_SECURE);      
		}
		
		setContentView(R.layout.activity_home);

		try
		{
			Iterator<String> i = savedInstanceState.keySet().iterator();
			while (i.hasNext())
			{
				String outState = i.next();
				if (outState.equals(Home.TAG) && savedInstanceState.getBoolean(Home.TAG))
				{
					initGallery = true;
				}
			}
		}
		catch (NullPointerException e)
		{
		}

		toEditor = new Intent(this, EditorActivity.class);
		toCamera = new Intent(this, CameraActivity.class);
		route = null;

		mainFragment = (HomeFragment) Fragment.instantiate(this, HomeFragment.class.getName());
		if (USE_USER_MANAGEMENT_FRAGMENT == 1)
			userManagementFragment = Fragment.instantiate(this, UserManagementFragment.class.getName());
		galleryFragment = (GalleryFragment) Fragment.instantiate(this, GalleryFragment.class.getName());
		cameraFragment = Fragment.instantiate(this, CameraFragment.class.getName());

		fragments.add(mainFragment);
		if (USE_USER_MANAGEMENT_FRAGMENT == 1)
			fragments.add(userManagementFragment);
		fragments.add(galleryFragment);
		fragments.add(cameraFragment);

		init = getIntent();

		initLayout();
		launchMain();
	}

	@Override
	public void onResume()
	{
		super.onResume();

		cacheWord.reattach();

        setNewLocale();
		
		if (informaCam.getCredentialManagerStatus() == org.witness.informacam.utils.Constants.Codes.Status.LOCKED)
		{
			informaCam.attemptLogout();
			finish();
			return;
		}
		
		informaCam.setEventListener(this);
		informaCam.setListAdapterListener(this);


		if(getIntent().hasExtra(Constants.Codes.Extras.CHANGE_LOCALE)) {
			getIntent().removeExtra(Constants.Codes.Extras.CHANGE_LOCALE);
		}

		if (getIntent().hasExtra(Constants.Codes.Extras.GENERATING_KEY)) {
			mFirstTime = true;
			mainFragment.setIsGeneratingKey(getIntent().getBooleanExtra(Constants.Codes.Extras.GENERATING_KEY, false));
			getIntent().removeExtra(Constants.Codes.Extras.GENERATING_KEY);
		} else {
			mainFragment.setIsGeneratingKey(false);
		}

		informaCam = (InformaCam) getApplication();

		if (init.getData() != null)
		{
			final Uri ictdURI = init.getData();

			mHandlerUI.post(new Runnable() {
				@SuppressWarnings("unused")
				@Override
				public void run() {
					IOrganization organization = informaCam.installICTD(ictdURI, mHandlerUI, HomeActivity.this);
					if(organization != null) {
						if (USE_USER_MANAGEMENT_FRAGMENT == 1)
							viewPager.setCurrentItem(INDEX_USER_MANAGEMENT);
						else
							viewPager.setCurrentItem(INDEX_MAIN);
					}
					else
					{
						Toast.makeText(HomeActivity.this, getString(org.witness.informacam.R.string.could_not_import_ictd), Toast.LENGTH_LONG).show();
					}
				}
			});

		}
		
		if (mFirstTime)
		{
			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
			        switch (which){
			        case DialogInterface.BUTTON_POSITIVE:
			        	Intent intent = new Intent(HomeActivity.this,WebActivity.class);
						startActivity(intent);
						mFirstTime = false;
			            break;

			        case DialogInterface.BUTTON_NEGATIVE:
			            //No button clicked
			            break;
			        }
			    }
			};

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.welcome_to_camerav_would_you_like_to_read_the_user_guide_).setPositiveButton(android.R.string.yes, dialogClickListener)
			    .setNegativeButton(android.R.string.no, dialogClickListener).show();
			
		}
	}

	private void setNewLocale()
	{
		String localeCode = PreferenceManager.getDefaultSharedPreferences(this).getString("iw_language", "0");
		int localeIdx = Integer.parseInt(localeCode);
		String[] lang = getResources().getStringArray(org.witness.informacam.R.array.locales);

		Locale locale = new Locale(lang[localeIdx]);
		Locale.setDefault(locale);
		Configuration config = getResources().getConfiguration();
		if (Build.VERSION.SDK_INT >= 17)
			config.setLocale(locale);
		else
			config.locale = locale;
		getResources().updateConfiguration(config, getResources().getDisplayMetrics());


	}

	private void initLayout()
	{
		pager = new TabPager(getSupportFragmentManager());

		viewPager = (ViewPager) findViewById(R.id.view_pager_root);
		viewPager.setAdapter(pager);
		viewPager.setOnPageChangeListener(pager);

		launchMain();
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(Home.TAG, true);

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		
		cacheWord.detach();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		cacheWord.disconnectFromService();
	}

	@SuppressWarnings("deprecation")
	@Override
	public int[] getDimensions()
	{
		Display display = getWindowManager().getDefaultDisplay();
		return new int[] { display.getWidth(), display.getHeight() };
	}

	@Override
	public void launchCamera() {
		resetActionBar();
		
		boolean externalCamera = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefExternalCamera", false);
		
		if (toCamera.hasExtra(org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE))
			toCamera.removeExtra(org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE);
		
		if (!externalCamera)
			toCamera.putExtra(
					 org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE,
					 org.witness.informacam.utils.Constants.App.Camera.Type.SECURE_CAMERA);
		else
			toCamera.putExtra(
					 org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE,
					 org.witness.informacam.utils.Constants.App.Camera.Type.CAMERA);
		
		route = toCamera;
		startActivityForResult(toCamera, Routes.CAMERA);
	}

	@Override
	public void launchEditor(IMedia media)
	{
		toEditor.putExtra(Codes.Extras.EDIT_MEDIA, media._id);
		startActivityForResult(toEditor, Routes.EDITOR);
		route = toEditor;

	}

	@Override
	public void getContextualMenuFor(final INotification notification)
	{
		List<ContextMenuAction> actions = new Vector<ContextMenuAction>();

		ContextMenuAction action = new ContextMenuAction();
		action.label = getResources().getString(R.string.delete);
		action.ocl = new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mam.cancel();
				informaCam.notificationsManifest.getById(notification._id).delete();

				if (userManagementFragment != null)
					((ListAdapterListener) userManagementFragment).updateAdapter(Codes.Adapters.NOTIFICATIONS);
			}
		};
		actions.add(action);

		if (notification.canRetry)
		{
			action = new ContextMenuAction();
			action.label = getResources().getString(R.string.retry);
			action.ocl = new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mam.cancel();
					try {
						notification.retry();
					} catch (InstantiationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			actions.add(action);
		}

		mam = new MediaActionMenu(this, actions);
		mam.Show();
	}

	@Override
	public void getContextualMenuFor(final IOrganization organization)
	{
		List<ContextMenuAction> actions = new Vector<ContextMenuAction>();

		ContextMenuAction action = new ContextMenuAction();
		action.label = getResources().getString(R.string.send_message);
		action.ocl = new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				mam.cancel();
				new TextareaPopup(HomeActivity.this, organization)
				{
					@Override
					public void cancel()
					{
						// TODO: send a message...

						super.cancel();
					}
				};
			}
		};
		actions.add(action);

		action = new ContextMenuAction();
		action.label = getResources().getString(R.string.resend_credentials);
		action.ocl = new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				mam.cancel();
				informaCam.resendCredentials(organization);
				Toast.makeText(HomeActivity.this, getResources().getString(R.string.you_have_resent_your_credentials_to_x, organization.organizationName),
						Toast.LENGTH_LONG).show();
			}
		};
		actions.add(action);

		mam = new MediaActionMenu(this, actions);
		mam.Show();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void getContextualMenuFor(final IMedia media, View anchorView)
	{
		try
		{
			if (anchorView == null)
				return; // Need an anchor view
			
			LayoutInflater inflater = LayoutInflater.from(this);

			ViewGroup anchorRoot = null;
			anchorRoot = (ViewGroup) anchorView.getRootView();
			
			View content = inflater.inflate(R.layout.popup_media_context_menu, anchorRoot, false);
			content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			PopupWindow mMenuPopup = new PopupWindow(content, content.getMeasuredWidth(), content.getMeasuredHeight(), true);

			// Delete
			//
			View btnDelete = content.findViewById(R.id.btnDeleteMedia);
			btnDelete.setOnClickListener(new PopupClickListener(mMenuPopup)
			{
				@Override
				protected void onSelected()
				{
					if ((informaCam.mediaManifest.getById(media._id)).delete())
					{
						updateAdapter(0);
					}
				}
			});

			/**
			// Share
			//
			View btnShare = content.findViewById(R.id.btnShareMedia);
			btnShare.setOnClickListener(new PopupClickListener(mMenuPopup)
			{
				@Override
				protected void onSelected()
				{
					new SharePopup(HomeActivity.this, informaCam.mediaManifest.getById(media._id), true, false);
				}
			});	*/
			
			mMenuPopup.setOutsideTouchable(true);
			mMenuPopup.setBackgroundDrawable(new BitmapDrawable());
			mMenuPopup.showAsDropDown(anchorView, anchorView.getWidth(), -anchorView.getHeight());
				
			mMenuPopup.getContentView().setOnClickListener(new PopupClickListener(mMenuPopup));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void onBackPressed()
	{
		if (viewPager.getCurrentItem() != INDEX_MAIN)
		{
			viewPager.setCurrentItem(INDEX_MAIN);
		}
		else
		{
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void logoutUser()
	{
		setResult(Activity.RESULT_CANCELED, getIntent().putExtra(Codes.Extras.LOGOUT_USER, true));
		finish();
	}

	@Override
	public void onActivityResult(int requestCode, int responseCode, Intent data) {
		
		informaCam.setEventListener(this);
		informaCam.setListAdapterListener(this);
		
		if(responseCode == Activity.RESULT_OK) {
			switch(requestCode) {

			case Codes.Routes.CAMERA:
				viewPager.setCurrentItem(INDEX_GALLERY);

				informaCam.mediaManifest.sortBy(Models.IMediaManifest.Sort.DATE_DESC);
				/*
				 * XXX: Other developers, take note:
				 * 
				 * the returned media can be JSONified. It represents the media
				 * has been captured, but that may or may not have been
				 * processed.
				 * 
				 * InformaCam will send a message whenever a new media item has
				 * been processed. The message contains the
				 * Codes.Extras.Messages.DCIM.ADD code, which is handled by
				 * "onUpdate()"
				 */
				//IDCIMSerializable returnedMedia = (IDCIMSerializable) data.getSerializableExtra(Codes.Extras.RETURNED_MEDIA);
				//Logger.d(LOG, "new dcim:\n" + returnedMedia.asJson().toString());
				
				//if(!returnedMedia.dcimList.isEmpty()) {
			//		setPending(returnedMedia.dcimList.size(), 0);
			//	}

		//		informaCam.stopInforma(); //camera activity handles this
				route = null;
				break;
			case Codes.Routes.LOGOUT:
				logoutUser();
				break;
			case Codes.Routes.EDITOR:
				//informaCam.stopInforma();
				route = null;
				break;
			case Codes.Routes.WIPE:
				getIntent().putExtra(Codes.Extras.PERFORM_WIPE, true);
				logoutUser();
				break;
			case Routes.WIZARD:
				setNewLocale();

				break;
			}
		}
	}

	class TabPager extends FragmentStatePagerAdapter implements OnPageChangeListener
	{

		public TabPager(FragmentManager fragmentManager)
		{
			super(fragmentManager);
		}

		@Override
		public void onPageScrollStateChanged(int state)
		{
			if (state == ViewPager.SCROLL_STATE_IDLE && viewPager.getCurrentItem() == INDEX_GALLERY)
			{
				// Landed in the gallery. Make sure data is initialized/loaded now!
				galleryFragment.initData();
			}
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2)
		{
		}

		@Override
		public void onPageSelected(int page)
		{
			// tabHost.setCurrentTab(page);
			if (page == INDEX_CAMERA)
			{
				launchCamera();
			} else {
				//updateAdapter(0);
			}
			supportInvalidateOptionsMenu();
		}

		@Override
		public Fragment getItem(int which)
		{
			return fragments.get(which);
		}

		@Override
		public int getCount()
		{
			return fragments.size();
		}

	}

	/*
	private void routeUs()
	{
		if (informaCam.informaService != null)
			doRouteUs();
		else
			informaCam.startInforma();
	}
	
	private void doRouteUs()
	{
		if (route != null)
		{
			if (route.equals(toEditor))
			{
				startActivityForResult(toEditor, Routes.EDITOR);
			}
			else if (route.equals(toCamera))
			{
				startActivityForResult(toCamera, Routes.CAMERA);
			}
		}
	}*/

	@Override
	public void waiter(boolean show)
	{
		/*
		 * if(show) { waiter = new WaitPopup(this); } else { if(waiter != null)
		 * { waiter.cancel(); } }
		 */
	}

	@Override
	public void updateData(INotification notification, Message message)
	{
	}

	@Override
	public void updateData(IOrganization organization, Message message)
	{
	}

	@Override
	public void updateAdapter(int which)
	{
		if (informaCam.getCredentialManagerStatus() == org.witness.informacam.utils.Constants.Codes.Status.UNLOCKED)
		{
			for (Fragment f : fragments)
			{
				if (f instanceof ListAdapterListener)
				{
					((ListAdapterListener) f).updateAdapter(which);
				}
			}
		}
	}

	@Override
	public void setPending(int numPending, int numCompleted)
	{
		if (informaCam.getCredentialManagerStatus() == org.witness.informacam.utils.Constants.Codes.Status.UNLOCKED)
		{
			//Log.d(LOG, "Set pending: " + numPending + " and completed: " + numCompleted);
			for (Fragment f : fragments)
			{
				if (f instanceof ListAdapterListener)
				{
					((ListAdapterListener) f).setPending(numPending, numCompleted);					
				}
			}
		}

	}

	@Override
	public void setLocale(String newLocale)
	{
		lastLocale = newLocale;
	}

	@Override
	public String getLocale()
	{
		return lastLocale;
	}

	@Override
	public void onUpdate(final Message message)
	{
		int code = message.getData().getInt(Codes.Extras.MESSAGE_CODE);

		switch (code)
		{
		case org.witness.informacam.utils.Constants.Codes.Messages.DCIM.ADD:
			final Bundle data = message.getData();

			
			mHandlerUI.sendEmptyMessage(0);
			mHandlerUI.post(new Runnable()
			{
				@Override
				public void run()
				{
					setPending(data.getInt(Codes.Extras.NUM_PROCESSING), data.getInt(Codes.Extras.NUM_COMPLETED));
				}
			});

			break;
		case org.witness.informacam.utils.Constants.Codes.Messages.Transport.GENERAL_FAILURE:
			mHandlerUI.post(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(HomeActivity.this, message.getData().getString(Codes.Extras.GENERAL_FAILURE), Toast.LENGTH_LONG).show();
				}
			});
			break;

		case org.witness.informacam.utils.Constants.Codes.Messages.UI.REPLACE:
			mainFragment.setIsGeneratingKey(false);
			break;

		case org.witness.informacam.utils.Constants.Codes.Messages.Transport.ORBOT_UNINSTALLED:
			mHandlerUI.post(new Runnable() {
				@Override
				public void run() {
				//	OrbotHelper oh = new OrbotHelper(HomeActivity.this);
			//		oh.promptToInstall(HomeActivity.this);
				}
			});
			break;
		case org.witness.informacam.utils.Constants.Codes.Messages.Transport.ORBOT_NOT_RUNNING:
			mHandlerUI.post(new Runnable() {
				@Override
				public void run() {
				//	OrbotHelper oh = new OrbotHelper(HomeActivity.this);
				//	oh.requestOrbotStart(HomeActivity.this);
				}
			});
			break; 

		}
	}

	private final Handler mHandlerUI = new Handler()
	{

		@Override
		public void handleMessage(Message msg)
		{

			super.handleMessage(msg);

			updateAdapter(msg.what);
		}

	};

	@Override
	public void launchGallery()
	{
		//informaCam.stopInforma();
		viewPager.setCurrentItem(INDEX_GALLERY);
	}

	@Override
	public void launchVideo()
	{
		resetActionBar();
		
		boolean externalCamera = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefExternalCamera", false);

		if (!externalCamera)
			toCamera.putExtra(
					org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE,
					org.witness.informacam.utils.Constants.App.Camera.Type.SECURE_CAMCORDER);
		else
			toCamera.putExtra(
					org.witness.informacam.utils.Constants.Codes.Extras.CAMERA_TYPE,
					org.witness.informacam.utils.Constants.App.Camera.Type.CAMCORDER);
			
		route = toCamera;
		startActivityForResult(toCamera, Routes.CAMERA);
		
	}


	@Override
	public void launchMain()
	{
		viewPager.setCurrentItem(INDEX_MAIN);
		resetActionBar();
		mainFragment.initData();
		//routeUs();
	}

	private void resetActionBar()
	{
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setTitle(R.string.app_name);
		actionBar.setDisplayShowHomeEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setHomeButtonEnabled(true);
		actionBar.setIcon(R.mipmap.ic_launcher);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
	}

	@Override
	public void onMediaRecorderStop()
	{
	}

	@Override
	public void onCacheWordLocked() {
		logoutUser();
	}

	@Override
	public void onCacheWordOpened() {
		cacheWord.setTimeout(0);
		
	}

	@Override
	public void onCacheWordUninitialized() {
		finish();
	}
}
