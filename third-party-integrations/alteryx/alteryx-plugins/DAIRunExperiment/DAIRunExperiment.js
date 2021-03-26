const displayScoreSelect = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsClassification').getValue()) {
        document.getElementById('scoreclassification').style.display = 'initial'
        document.getElementById('scoreregression').style.display = 'none'
    } else {
        document.getElementById('scoreclassification').style.display = 'none'
        document.getElementById('scoreregression').style.display = 'initial'
    }
}

const toggleOAuth = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseOAuth').getValue()) {
        document.getElementById('usernameBox').style.display = 'none'
        document.getElementById('passwordBox').style.display = 'none'

        document.getElementById('endpointURL').style.display = 'initial'
        document.getElementById('introspectionURL').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
        document.getElementById('refreshToken').style.display = 'initial'
    } else {
        document.getElementById('usernameBox').style.display = 'initial'
        document.getElementById('passwordBox').style.display = 'initial'

        document.getElementById('endpointURL').style.display = 'none'
        document.getElementById('introspectionURL').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
        document.getElementById('refreshToken').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {

    const operationClassificationSelector = new AlteryxDataItems.StringSelector('ClassificationDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    const operationTimeSeriesSelector = new AlteryxDataItems.StringSelector('TimeSeriesDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').setValue('False')
    manager.addDataItem(operationTimeSeriesSelector)
    manager.bindDataItemToWidget(operationTimeSeriesSelector, 'TimeSeriesDropdown')
    displayScoreSelect()
    toggleOAuth()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayScoreSelect()
    toggleOAuth()
    Alteryx.Gui.Manager.getDataItem('IsClassification').registerPropertyListener('value', displayScoreSelect)
    Alteryx.Gui.Manager.getDataItem('UseOAuth').registerPropertyListener('value', toggleOAuth)
}