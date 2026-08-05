package com.myhome.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 通过 @Inject constructor 直接构造注入，无需 @Binds。
 * 保留空模块作为后续扩展位（如多实现切换时在此 @Binds）。
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
