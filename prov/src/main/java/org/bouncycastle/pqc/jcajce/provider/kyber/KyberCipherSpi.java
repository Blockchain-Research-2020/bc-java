package org.bouncycastle.pqc.jcajce.provider.kyber;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;

import org.bouncycastle.crypto.CryptoServicesRegistrar;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.crypto.Wrapper;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jcajce.spec.KEMParameterSpec;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMExtractor;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMGenerator;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.bouncycastle.pqc.jcajce.provider.util.WrapUtil;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Exceptions;
import org.bouncycastle.util.Strings;

class KyberCipherSpi
        extends CipherSpi
{
    private final String algorithmName;
    private KyberKEMGenerator kemGen;
    private KEMParameterSpec kemParameterSpec;
    private BCKyberPublicKey wrapKey;
    private BCKyberPrivateKey unwrapKey;

    private AlgorithmParameters engineParams;
    private KyberParameters kyberParameters;

    KyberCipherSpi(String algorithmName)
    {
        this.algorithmName = algorithmName;
        this.kyberParameters = null;
    }

    KyberCipherSpi(KyberParameters kyberParameters)
    {
        this.kyberParameters = kyberParameters;
        this.algorithmName = Strings.toUpperCase(kyberParameters.getName());
    }

    @Override
    protected void engineSetMode(String mode)
            throws NoSuchAlgorithmException
    {
        throw new NoSuchAlgorithmException("Cannot support mode " + mode);
    }

    @Override
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException
    {
        throw new NoSuchPaddingException("Padding " + padding + " unknown");
    }

    protected int engineGetKeySize(
            Key key)
    {
        return 2048; // TODO
        //throw new IllegalArgumentException("not an valid key!");
    }

    @Override
    protected int engineGetBlockSize()
    {
        return 0;
    }

    @Override
    protected int engineGetOutputSize(int i)
    {
        return -1;        // can't use with update/doFinal
    }

    @Override
    protected byte[] engineGetIV()
    {
        return null;
    }

    @Override
    protected AlgorithmParameters engineGetParameters()
    {
        if (engineParams == null)
        {
            try
            {
                engineParams = AlgorithmParameters.getInstance(algorithmName, "BCPQC");

                engineParams.init(kemParameterSpec);
            }
            catch (Exception e)
            {
                throw Exceptions.illegalStateException(e.toString(), e);
            }
        }

        return engineParams;
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException
    {
        try
        {
            engineInit(opmode, key, (AlgorithmParameterSpec)null, random);
        }
        catch (InvalidAlgorithmParameterException e)
        {
            throw Exceptions.illegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec paramSpec, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        if (paramSpec == null)
        {
            // TODO: default should probably use shake.
            kemParameterSpec = new KEMParameterSpec("AES-KWP");
        }
        else
        {
            if (!(paramSpec instanceof KEMParameterSpec))
            {
                throw new InvalidAlgorithmParameterException(algorithmName + " can only accept KTSParameterSpec");
            }

            kemParameterSpec = (KEMParameterSpec)paramSpec;
        }

        if (opmode == Cipher.WRAP_MODE)
        {
            if (key instanceof BCKyberPublicKey)
            {
                wrapKey = (BCKyberPublicKey)key;
                kemGen = new KyberKEMGenerator(CryptoServicesRegistrar.getSecureRandom(random));
            }
            else
            {
                throw new InvalidKeyException("Only a " + algorithmName + " public key can be used for wrapping");
            }
        }
        else if (opmode == Cipher.UNWRAP_MODE)
        {
            if (key instanceof BCKyberPrivateKey)
            {
                unwrapKey = (BCKyberPrivateKey)key;
            }
            else
            {
                throw new InvalidKeyException("Only a " + algorithmName + " private key can be used for unwrapping");
            }
        }
        else
        {
            throw new InvalidParameterException("Cipher only valid for wrapping/unwrapping");
        }

        if (kyberParameters != null)
        {
            String canonicalAlgName = Strings.toUpperCase(kyberParameters.getName());
            if (!canonicalAlgName.equals(key.getAlgorithm()))
            {
                throw new InvalidKeyException("cipher locked to " + canonicalAlgName);
            }
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters algorithmParameters, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        AlgorithmParameterSpec paramSpec = null;

        if (algorithmParameters != null)
        {
            try
            {
                paramSpec = algorithmParameters.getParameterSpec(KEMParameterSpec.class);
            }
            catch (Exception e)
            {
                throw new InvalidAlgorithmParameterException("can't handle parameter " + algorithmParameters.toString());
            }
        }

        engineInit(opmode, key, paramSpec, random);
    }

    @Override
    protected byte[] engineUpdate(byte[] bytes, int i, int i1)
    {
        throw new IllegalStateException("Not supported in a wrapping mode");
    }

    @Override
    protected int engineUpdate(byte[] bytes, int i, int i1, byte[] bytes1, int i2)
            throws ShortBufferException
    {
        throw new IllegalStateException("Not supported in a wrapping mode");
    }

    @Override
    protected byte[] engineDoFinal(byte[] bytes, int i, int i1)
            throws IllegalBlockSizeException, BadPaddingException
    {
        throw new IllegalStateException("Not supported in a wrapping mode");
    }

    @Override
    protected int engineDoFinal(byte[] bytes, int i, int i1, byte[] bytes1, int i2)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException
    {
        throw new IllegalStateException("Not supported in a wrapping mode");
    }

    protected byte[] engineWrap(
            Key key)
            throws IllegalBlockSizeException, InvalidKeyException
    {
        byte[] encoded = key.getEncoded();
        if (encoded == null)
        {
            throw new InvalidKeyException("Cannot wrap key, null encoding.");
        }

        try
        {
            SecretWithEncapsulation secEnc = kemGen.generateEncapsulated(wrapKey.getKeyParams());

            Wrapper kWrap = WrapUtil.getWrapper(kemParameterSpec.getKeyAlgorithmName());

            KeyParameter keyParameter = new KeyParameter(secEnc.getSecret());

            kWrap.init(true, keyParameter);

            byte[] encapsulation = secEnc.getEncapsulation();

            secEnc.destroy();

            byte[] keyToWrap = key.getEncoded();

            byte[] rv = Arrays.concatenate(encapsulation, kWrap.wrap(keyToWrap, 0, keyToWrap.length));

            Arrays.clear(keyToWrap);

            return rv;
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalBlockSizeException("unable to generate KTS secret: " + e.getMessage());
        }
        catch (DestroyFailedException e)
        {
            throw new IllegalBlockSizeException("unable to destroy interim values: " + e.getMessage());
        }
    }

    protected Key engineUnwrap(
            byte[] wrappedKey,
            String wrappedKeyAlgorithm,
            int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException
    {
        // TODO: add support for other types.
        if (wrappedKeyType != Cipher.SECRET_KEY)
        {
            throw new InvalidKeyException("only SECRET_KEY supported");
        }
        try
        {
            KyberKEMExtractor kemExt = new KyberKEMExtractor(unwrapKey.getKeyParams());

            byte[] secret = kemExt.extractSecret(Arrays.copyOfRange(wrappedKey, 0, kemExt.getEncapsulationLength()));

            Wrapper kWrap = WrapUtil.getWrapper(kemParameterSpec.getKeyAlgorithmName());

            KeyParameter keyParameter = new KeyParameter(secret);

            Arrays.clear(secret);

            kWrap.init(false, keyParameter);

            byte[] keyEncBytes = Arrays.copyOfRange(wrappedKey, kemExt.getEncapsulationLength(), wrappedKey.length);

            SecretKey rv = new SecretKeySpec(kWrap.unwrap(keyEncBytes, 0, keyEncBytes.length), wrappedKeyAlgorithm);

            Arrays.clear(keyParameter.getKey());

            return rv;
        }
        catch (IllegalArgumentException e)
        {
            throw new NoSuchAlgorithmException("unable to extract KTS secret: " + e.getMessage());
        }
        catch (InvalidCipherTextException e)
        {
            throw new InvalidKeyException("unable to extract KTS secret: " + e.getMessage());
        }
    }

    public static class Base
            extends KyberCipherSpi
    {
        public Base()
                throws NoSuchAlgorithmException
        {
            super("KYBER");
        }
    }

    public static class Kyber512
        extends KyberCipherSpi
    {
        public Kyber512()
        {
            super(KyberParameters.kyber512);
        }
    }

    public static class Kyber768
        extends KyberCipherSpi
    {
        public Kyber768()
        {
            super(KyberParameters.kyber768);
        }
    }

    public static class Kyber1024
        extends KyberCipherSpi
    {
        public Kyber1024()
        {
            super(KyberParameters.kyber1024);
        }
    }

    public static class Kyber512_AES
        extends KyberCipherSpi
    {
        public Kyber512_AES()
        {
            super(KyberParameters.kyber512_aes);
        }
    }

    public static class Kyber768_AES
        extends KyberCipherSpi
    {
        public Kyber768_AES()
        {
            super(KyberParameters.kyber768_aes);
        }
    }

    public static class Kyber1024_AES
        extends KyberCipherSpi
    {
        public Kyber1024_AES()
        {
            super(KyberParameters.kyber1024_aes);
        }
    }
}
