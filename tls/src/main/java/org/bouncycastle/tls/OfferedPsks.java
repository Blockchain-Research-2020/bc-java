package org.bouncycastle.tls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoUtils;
import org.bouncycastle.tls.crypto.TlsHash;
import org.bouncycastle.tls.crypto.TlsHashOutputStream;
import org.bouncycastle.tls.crypto.TlsSecret;

public class OfferedPsks
{
    protected final Vector identities;
    protected final Vector binders;

    public OfferedPsks(Vector identities)
    {
        this(identities, null);
    }

    private OfferedPsks(Vector identities, Vector binders)
    {
        if (null == identities || identities.isEmpty())
        {
            throw new IllegalArgumentException("'identities' cannot be null or empty");
        }
        if (null != binders && identities.size() != binders.size())
        {
            throw new IllegalArgumentException("'binders' must be the same length as 'identities' (or null)");
        }

        this.identities = identities;
        this.binders = binders;
    }

    public Vector getBinders()
    {
        return binders;
    }

    public Vector getIdentities()
    {
        return identities;
    }

    void encode(OutputStream output) throws IOException
    {
        // identities
        {
            int lengthOfIdentitiesList = 0;
            for (int i = 0; i < identities.size(); ++i)
            {
                PskIdentity identity = (PskIdentity)identities.elementAt(i);
                lengthOfIdentitiesList += identity.getEncodedLength();
            }
    
            TlsUtils.checkUint16(lengthOfIdentitiesList);
            TlsUtils.writeUint16(lengthOfIdentitiesList, output);
    
            for (int i = 0; i < identities.size(); ++i)
            {
                PskIdentity identity = (PskIdentity)identities.elementAt(i);
                identity.encode(output);
            }
        }

        // binders
        if (null != binders)
        {
            int lengthOfBindersList = 0;
            for (int i = 0; i < binders.size(); ++i)
            {
                byte[] binder = (byte[])binders.elementAt(i);
                lengthOfBindersList += 1 + binder.length;
            }

            TlsUtils.checkUint16(lengthOfBindersList);
            TlsUtils.writeUint16(lengthOfBindersList, output);

            for (int i = 0; i < binders.size(); ++i)
            {
                byte[] binder = (byte[])binders.elementAt(i);
                TlsUtils.writeOpaque8(binder, output);
            }
        }
    }

    static void encodeBinders(OutputStream output, TlsCrypto crypto, TlsHandshakeHash handshakeHash,
        TlsPSK[] psks, TlsSecret[] earlySecrets, int expectedLengthOfBindersList) throws IOException
    {
        TlsUtils.checkUint16(expectedLengthOfBindersList);
        TlsUtils.writeUint16(expectedLengthOfBindersList, output);

        int lengthOfBindersList = 0;
        for (int i = 0; i < psks.length; ++i)
        {
            TlsPSK psk = psks[i];
            TlsSecret earlySecret = earlySecrets[i];

            // TODO[tls13-psk] Handle resumption PSKs
            boolean isExternalPSK = true;
            int pskCryptoHashAlgorithm = TlsCryptoUtils.getHashForPRF(psk.getPRFAlgorithm());

            // TODO[tls13-psk] Cache the transcript hashes per algorithm to avoid duplicates for multiple PSKs
            TlsHash hash = crypto.createHash(pskCryptoHashAlgorithm);
            handshakeHash.copyBufferTo(new TlsHashOutputStream(hash));
            byte[] transcriptHash = hash.calculateHash();

            byte[] binder = TlsUtils.calculatePSKBinder(crypto, isExternalPSK, pskCryptoHashAlgorithm, earlySecret,
                transcriptHash);

            lengthOfBindersList += 1 + binder.length;
            TlsUtils.writeOpaque8(binder, output);
        }

        if (expectedLengthOfBindersList != lengthOfBindersList)
        {
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
    }

    static int getLengthOfBindersList(TlsPSK[] psks) throws IOException
    {
        int lengthOfBindersList = 0;
        for (int i = 0; i < psks.length; ++i)
        {
            TlsPSK psk = psks[i];

            int prfAlgorithm = psk.getPRFAlgorithm();
            int prfCryptoHashAlgorithm = TlsCryptoUtils.getHashForPRF(prfAlgorithm);

            lengthOfBindersList += 1 + TlsCryptoUtils.getHashOutputSize(prfCryptoHashAlgorithm);
        }
        TlsUtils.checkUint16(lengthOfBindersList);
        return lengthOfBindersList;
    }

    public static OfferedPsks parse(InputStream input) throws IOException
    {
        Vector identities = new Vector();
        {
            int totalLengthIdentities = TlsUtils.readUint16(input);
            if (totalLengthIdentities < 7)
            {
                throw new TlsFatalAlert(AlertDescription.decode_error);
            }

            byte[] identitiesData = TlsUtils.readFully(totalLengthIdentities, input);
            ByteArrayInputStream buf = new ByteArrayInputStream(identitiesData);
            do
            {
                PskIdentity identity = PskIdentity.parse(buf);
                identities.add(identity);
            }
            while (buf.available() > 0);
        }

        Vector binders = new Vector();
        {
            int totalLengthBinders = TlsUtils.readUint16(input);
            if (totalLengthBinders < 33)
            {
                throw new TlsFatalAlert(AlertDescription.decode_error);
            }

            byte[] bindersData = TlsUtils.readFully(totalLengthBinders, input);
            ByteArrayInputStream buf = new ByteArrayInputStream(bindersData);
            do
            {
                byte[] binder = TlsUtils.readOpaque8(input, 32);
                binders.add(binder);
            }
            while (buf.available() > 0);
        }

        return new OfferedPsks(identities, binders);
    }
}
