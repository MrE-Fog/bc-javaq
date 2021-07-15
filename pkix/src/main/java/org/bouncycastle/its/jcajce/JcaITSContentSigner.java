package org.bouncycastle.its.jcajce;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.its.ITSCertificate;
import org.bouncycastle.its.operator.ITSContentSigner;
import org.bouncycastle.jcajce.util.DefaultJcaJceHelper;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Arrays;

public class JcaITSContentSigner
    implements ITSContentSigner
{

    private final ECPrivateKey privateKey;
    private final ITSCertificate signerCert;
    private final AlgorithmIdentifier digestAlgo;
    private final DigestCalculator digest;
    private final byte[] parentData;
    private final ASN1ObjectIdentifier curveID;
    private final byte[] parentDigest;
    private final String signer;
    private final JcaJceHelper provider;

    public JcaITSContentSigner(ECPrivateKey privateKey)
    {
        this(privateKey, null, new DefaultJcaJceHelper());
    }

    public JcaITSContentSigner(ECPrivateKey privateKey, ITSCertificate signerCert, JcaJceHelper provider)
    {
        this.privateKey = privateKey;
        this.signerCert = signerCert;
        this.provider = provider;

        //
        // Probably the most generic way at the moment.
        //

        PrivateKeyInfo pkInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
        curveID = ASN1ObjectIdentifier.getInstance(pkInfo.getPrivateKeyAlgorithm().getParameters());

        if (curveID.equals(SECObjectIdentifiers.secp256r1))
        {
            digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
            signer = "SHA256withECDSA";
        }
        else if (curveID.equals(TeleTrusTObjectIdentifiers.brainpoolP256r1))
        {
            digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
            signer = "SHA256withECDSA";
        }
        else if (curveID.equals(TeleTrusTObjectIdentifiers.brainpoolP384r1))
        {
            digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384);
            signer = "SHA384withECDSA";
        }
        else
        {
            throw new IllegalArgumentException("unknown key type");
        }

        DigestCalculatorProvider digestCalculatorProvider;

        try
        {

            JcaDigestCalculatorProviderBuilder bld = new JcaDigestCalculatorProviderBuilder().setHelper(provider);
            digestCalculatorProvider = bld.build();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        try
        {
            digest = digestCalculatorProvider.get(digestAlgo);
        }
        catch (OperatorCreationException e)
        {
            throw new IllegalStateException("cannot recognise digest type: " + digestAlgo.getAlgorithm(), e);
        }

        if (signerCert != null)
        {
            try
            {
                parentData = signerCert.getEncoded();
                digest.getOutputStream().write(parentData, 0, parentData.length);
                parentDigest = digest.getDigest();
            }
            catch (IOException e)
            {
                throw new IllegalStateException("signer certificate encoding failed: " + e.getMessage());
            }
        }
        else
        {
            // self signed so we use a null digest for the parent.
            this.parentData = null;
            this.parentDigest = digest.getDigest();
        }

    }

    @Override
    public OutputStream getOutputStream()
    {
        return digest.getOutputStream();
    }

    @Override
    public byte[] getSignature()
    {
        byte[] clientCertDigest = digest.getDigest();
        Signature signature;
        try
        {
            signature = provider.createSignature(signer);
            signature.initSign(privateKey);
            signature.update(clientCertDigest, 0, clientCertDigest.length);
            signature.update(digest.getDigest());
            return signature.sign();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public ITSCertificate getAssociatedCertificate()
    {
        return signerCert;
    }

    @Override
    public byte[] getAssociatedCertificateDigest()
    {
        return Arrays.clone(parentDigest);
    }

    @Override
    public AlgorithmIdentifier getDigestAlgorithm()
    {
        return digestAlgo;
    }

    @Override
    public boolean isForSelfSigning()
    {
        return parentData == null;
    }
}