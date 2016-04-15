/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.verity;

import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class VeritySigner {

    private static void usage() {
        System.err.println("usage: VeritySigner <contentfile> <key.pk8> " +
                "<sigfile> | <contentfile> <certificate.x509.pem> <sigfile> " +
                "-verify");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            usage();
            return;
        }

        Security.addProvider(new BouncyCastleProvider());

        byte[] content = Utils.read(args[0]);

        if (args.length > 3 && "-verify".equals(args[3])) {
            X509Certificate cert = Utils.loadPEMCertificate(args[1]);
            PublicKey publicKey = cert.getPublicKey();

            byte[] signature = Utils.read(args[2]);

            try {
                if (Utils.verify(publicKey, content, signature,
                            Utils.getSignatureAlgorithmIdentifier(publicKey))) {
                    System.err.println("Signature is VALID");
                    System.exit(0);
                } else {
                    System.err.println("Signature is INVALID");
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

            System.exit(1);
        } else {
            PrivateKey privateKey = Utils.loadDERPrivateKey(Utils.read(args[1]));
            byte[] signature = Utils.sign(privateKey, content);
            Utils.write(signature, args[2]);
        }
    }
}
