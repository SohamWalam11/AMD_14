package com.prism.state

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.prism.state.proto.UserProfileProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object UserProfileSerializer : Serializer<UserProfileProto> {
    override val defaultValue: UserProfileProto = UserProfileProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserProfileProto {
        try {
            return UserProfileProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: UserProfileProto, output: OutputStream) = t.writeTo(output)
}

val Context.userProfileDataStore: DataStore<UserProfileProto> by dataStore(
    fileName = "user_profile.pb",
    serializer = UserProfileSerializer
)
