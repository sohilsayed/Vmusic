// File: java\com\example\holodex\di\Qualifiers.kt
package com.example.holodex.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpstreamDataSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HolodexHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicdexHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedMusicdexHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher