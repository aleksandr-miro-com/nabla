package com.miro.demo

import com.miro.collaboration.engine.core.auth.session.Credential
import com.miro.collaboration.engine.core.auth.session.CredentialProvider
import com.miro.collaboration.engine.core.auth.session.HandshakeInfo
import org.springframework.stereotype.Component

@Component
class DemoCredentialProvider : CredentialProvider {
    override suspend fun acquire(info: HandshakeInfo): Credential {
        return DemoCredential()
    }

    override suspend fun refresh(current: Credential): Credential {
        return current
    }

    override fun isTerminalError(e: Throwable): Boolean {
        return false
    }
}