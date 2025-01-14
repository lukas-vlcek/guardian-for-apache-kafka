package io.aiven.guardian.kafka
package s3

import akka.stream.alpakka.s3.MetaHeaders
import akka.stream.alpakka.s3.S3Headers
import akka.stream.alpakka.s3.headers.CannedAcl
import akka.stream.alpakka.s3.headers.ServerSideEncryption
import akka.stream.alpakka.s3.headers.StorageClass
import io.aiven.guardian.kafka.PureConfigUtils._
import io.aiven.guardian.kafka.s3.configs.S3
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import pureconfig.ConfigReader._
import pureconfig.ConfigSource

import scala.annotation.nowarn

trait Config {

  // TODO Unfortunately the following boilerplate is here because the  S3 Alpakka providers no public constructors
  // for S3Headers apart from the limited S3Headers(). This means we can't use pureconfig.generic.auto._ and hence
  // we have to write this out manually

  implicit val cannedACLConfigReader: ConfigReader[CannedAcl] = (cur: ConfigCursor) =>
    cur.asString.flatMap {
      case CannedAcl.AuthenticatedRead.value      => Right(CannedAcl.AuthenticatedRead)
      case CannedAcl.AwsExecRead.value            => Right(CannedAcl.AwsExecRead)
      case CannedAcl.BucketOwnerFullControl.value => Right(CannedAcl.BucketOwnerFullControl)
      case CannedAcl.BucketOwnerRead.value        => Right(CannedAcl.BucketOwnerRead)
      case CannedAcl.Private.value                => Right(CannedAcl.Private)
      case CannedAcl.PublicRead.value             => Right(CannedAcl.PublicRead)
      case CannedAcl.PublicReadWrite.value        => Right(CannedAcl.PublicReadWrite)
      case rest                                   => Left(failure(cur, rest, "CannedAcl"))
    }

  implicit val metaHeadersConfigReader: ConfigReader[MetaHeaders] = mapReader[String].map(MetaHeaders.apply)

  implicit val storageClassConfigReader: ConfigReader[StorageClass] = (cur: ConfigCursor) =>
    cur.asString.flatMap {
      case StorageClass.Standard.storageClass          => Right(StorageClass.Standard)
      case StorageClass.InfrequentAccess.storageClass  => Right(StorageClass.InfrequentAccess)
      case StorageClass.Glacier.storageClass           => Right(StorageClass.Glacier)
      case StorageClass.ReducedRedundancy.storageClass => Right(StorageClass.ReducedRedundancy)
      case rest                                        => Left(failure(cur, rest, "StorageClass"))
    }

  implicit val serverSideEncryptionReader: ConfigReader[ServerSideEncryption] = (cur: ConfigCursor) =>
    cur.fluent.at("type").asString.flatMap {
      case "aes256" =>
        Right(ServerSideEncryption.aes256())
      case "kms" =>
        ConfigReader
          .forProduct2("key-id", "context") { (keyId: String, context: Option[String]) =>
            val base = ServerSideEncryption.kms(keyId)
            context.fold(base)(base.withContext)
          }
          .from(cur)
      case "customer-keys" =>
        ConfigReader
          .forProduct2("key", "md5") { (key: String, md5: Option[String]) =>
            val base = ServerSideEncryption.customerKeys(key)
            md5.fold(base)(base.withMd5)
          }
          .from(cur)
    }

  implicit val s3HeadersConfigReader: ConfigReader[S3Headers] =
    ConfigReader.forProduct5("canned-acl",
                             "meta-headers",
                             "storage-class",
                             "custom-headers",
                             "server-side-encryption"
    ) {
      (cannedAcl: Option[CannedAcl],
       metaHeaders: Option[MetaHeaders],
       storageClass: Option[StorageClass],
       customHeaders: Option[Map[String, String]],
       serverSideEncryption: Option[ServerSideEncryption]
      ) =>
        val base  = S3Headers()
        val base2 = cannedAcl.fold(base)(base.withCannedAcl)
        val base3 = metaHeaders.fold(base2)(base2.withMetaHeaders)
        val base4 = storageClass.fold(base3)(base3.withStorageClass)
        val base5 = customHeaders.fold(base4)(base4.withCustomHeaders)
        serverSideEncryption.fold(base5)(base5.withServerSideEncryption)
    }

  implicit lazy val s3Headers: S3Headers = ConfigSource.default.at("s3-headers").loadOrThrow[S3Headers]

  @nowarn("cat=lint-byname-implicit")
  implicit lazy val s3Config: S3 = {
    import pureconfig.generic.auto._
    ConfigSource.default.at("s3-config").loadOrThrow[S3]
  }
}

object Config extends Config
