package com.miro.demo

import com.miro.collaboration.engine.core.auth.session.Credential

class DemoCredential : Credential {
    override val expiresAtMillis get() = Long.MAX_VALUE
}