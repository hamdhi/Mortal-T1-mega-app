package lk.mortal.tv

object UpdatePolicy {
    fun verifyPackage(expectedPackage: String, actualPackage: String, installedCode: Long, expectedCode: Long, actualCode: Long, currentCertificates: Set<String>, updateCertificates: Set<String>) {
        require(actualPackage == expectedPackage) { "Package identity mismatch" }
        require(actualCode == expectedCode && actualCode > installedCode) { "Version mismatch or update is not newer" }
        require(currentCertificates.isNotEmpty() && currentCertificates == updateCertificates) { "Signing certificate mismatch. Use an update signed by the original publisher." }
    }
}
