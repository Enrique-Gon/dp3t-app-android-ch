/*
 * Created by Ubique Innovation AG
 * https://www.ubique.ch
 * Copyright (c) 2020. All rights reserved.
 */
package org.dpppt.android.app;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.crashlytics.android.Crashlytics;

import org.dpppt.android.app.storage.SecureStorage;
import org.dpppt.android.app.util.NotificationUtil;
import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.InfectionStatus;
import org.dpppt.android.sdk.TracingStatus;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.backend.BackendBucketRepository;
import org.dpppt.android.sdk.internal.database.models.MatchedContact;
import org.dpppt.android.sdk.internal.util.ProcessUtil;

import io.fabric.sdk.android.Fabric;


public class MainApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		if (BuildConfig.IS_DEV) {
			BackendBucketRepository.BATCH_LENGTH = 5 * 60 * 1000L;
		}
		if (ProcessUtil.isMainProcess(this)) {
			registerReceiver(contactUpdateReceiver, DP3T.getUpdateIntentFilter());
			DP3T.init(this, new ApplicationInfo("dp3t-app", BuildConfig.REPORT_URL, BuildConfig.BUCKET_URL));
		}
		if (!BuildConfig.DEBUG) {
			Fabric.with(this, new Crashlytics());
		}
	}

	private BroadcastReceiver contactUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			SecureStorage secureStorage = SecureStorage.getInstance(context);
			TracingStatus status = DP3T.getStatus(context);
			if (status.getInfectionStatus() == InfectionStatus.EXPOSED) {
				MatchedContact newestContact = null;
				long dateNewest = 0;
				for (MatchedContact contact : status.getMatchedContacts()) {
					if (contact.getReportDate() > dateNewest) {
						newestContact = contact;
						dateNewest = contact.getReportDate();
					}
				}
				if (newestContact != null && secureStorage.getLastShownContactId() != newestContact.getId()) {
					createNewContactNotifaction(context, newestContact.getId());
				}
			}
		}
	};

	private void createNewContactNotifaction(Context context, int contactId) {
		SecureStorage secureStorage = SecureStorage.getInstance(context);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationUtil.createNotificationChannel(context);
		}

		Intent resultIntent = new Intent(context, MainActivity.class);
		resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		resultIntent.setAction(MainActivity.ACTION_GOTO_REPORTS);

		PendingIntent pendingIntent =
				PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification notification =
				new NotificationCompat.Builder(context, NotificationUtil.NOTIFICATION_CHANNEL_ID)
						.setContentTitle(context.getString(R.string.push_exposed_title))
						.setContentText(context.getString(R.string.push_exposed_text))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setSmallIcon(R.drawable.ic_begegnungen)
						.setContentIntent(pendingIntent)
						.setAutoCancel(true)
						.build();

		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(NotificationUtil.NOTIFICATION_ID_CONTACT, notification);

		secureStorage.setHotlineCallPending(true);
		secureStorage.setReportsHeaderAnimationPending(true);
		secureStorage.setLastShownContactId(contactId);
	}

}
