/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.transport.lwm2m.server.credentials;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.util.Hex;
import org.eclipse.leshan.core.util.SecurityUtil;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

@Slf4j
@Component("LwM2MCredentials")
@ConditionalOnExpression("'${service.type:null}'=='tb-transport' || ('${service.type:null}'=='monolith' && '${transport.lwm2m.enabled}'=='true')")
public class LwM2MCredentials {

    public SecurityInfo getSecurityInfo(String endpoint, String jsonStr) {
        SecurityInfo securityInfo = null;
        JsonObject object = validateJson(jsonStr);
        if (!object.isJsonNull()) {
            if (!object.isJsonNull()) {
                boolean isX509 = (object.has("x509") && !object.get("x509").isJsonNull() && object.get("x509").isJsonPrimitive()) ? object.get("x509").getAsBoolean() : false;
                boolean isPsk = (object.has("psk") && !object.get("psk").isJsonNull()) ? true : false;
                boolean isRpk = (!isX509 && object.has("rpk") && !object.get("rpk").isJsonNull()) ? true : false;
                if (isX509) {
                    securityInfo = SecurityInfo.newX509CertInfo(endpoint);
                } else if (isPsk) {
                    // PSK Deserialization
                    JsonObject psk = (object.get("psk").isJsonObject()) ? object.get("psk").getAsJsonObject() : null;
                    if (!psk.isJsonNull()) {
                        String identity = null;
                        if (psk.has("identity") && psk.get("identity").isJsonPrimitive()) {
                            identity = psk.get("identity").getAsString();
                        } else {
                            log.error("Missing PSK identity");
                        }
                        if (identity != null && !identity.isEmpty()) {
                            byte[] key = new byte[0];
                            try {
                                if (psk.has("key") && psk.get("key").isJsonPrimitive()) {
                                    key = Hex.decodeHex(psk.get("key").getAsString().toCharArray());
                                } else {
                                    log.error("Missing PSK key");
                                }
                            } catch (IllegalArgumentException e) {
                                log.error("Missing PSK key: " + e.getMessage());
                            }
                            securityInfo = SecurityInfo.newPreSharedKeyInfo(endpoint, identity, key);
                        }
                    }
                } else if (isRpk) {
                    JsonObject rpk = (object.get("rpk").isJsonObject()) ? object.get("rpk").getAsJsonObject() : null;
                    if (!rpk.isJsonNull()) {
                        PublicKey key = null;
                        try {
                            if (rpk.has("key") && rpk.get("key").isJsonPrimitive()) {
                                byte[] rpkkey = Hex.decodeHex(rpk.get("key").getAsString().toCharArray());
                                key = SecurityUtil.publicKey.decode(rpkkey);
                            } else {
                                log.error("Missing RPK key");
                            }
                        } catch (IllegalArgumentException | IOException | GeneralSecurityException e) {
                            log.error("RPK: Invalid security info content: " + e.getMessage());
                        }
                        securityInfo = SecurityInfo.newRawPublicKeyInfo(endpoint, key);
                    }
                }

            }
        }
        return securityInfo;
    }

    private JsonObject validateJson(String jsonStr) {
        JsonObject object = null;
        String jsonValidFlesh = jsonStr.replaceAll("\\\\", "");
        String jsonValid = (jsonValidFlesh.substring(0, 1).equals("\"") && jsonValidFlesh.substring(jsonValidFlesh.length() - 1).equals("\"")) ? jsonValidFlesh.substring(1, jsonValidFlesh.length() - 1) : jsonValidFlesh;
        try {
            object = new JsonParser().parse(jsonValid).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            log.error("[{}] Fail validateJson [{}]", jsonStr, e.getMessage());
        }
        return object;
    }


}
