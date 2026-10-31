package lk.mortal.tv

import org.junit.Test

class UpdatePolicyTest {
    @Test fun acceptsSamePublisherUpgrade() = UpdatePolicy.verifyPackage("app", "app", 1, 2, 2, setOf("cert"), setOf("cert"))
    @Test(expected = IllegalArgumentException::class) fun rejectsDowngrade() = UpdatePolicy.verifyPackage("app", "app", 2, 1, 1, setOf("cert"), setOf("cert"))
    @Test(expected = IllegalArgumentException::class) fun rejectsMetadataMismatch() = UpdatePolicy.verifyPackage("app", "app", 1, 3, 2, setOf("cert"), setOf("cert"))
    @Test(expected = IllegalArgumentException::class) fun rejectsOtherPackage() = UpdatePolicy.verifyPackage("app", "other", 1, 2, 2, setOf("cert"), setOf("cert"))
    @Test(expected = IllegalArgumentException::class) fun rejectsOtherSigner() = UpdatePolicy.verifyPackage("app", "app", 1, 2, 2, setOf("cert"), setOf("attacker"))
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingSigners() = UpdatePolicy.verifyPackage("app", "app", 1, 2, 2, emptySet(), emptySet())
}
